#!/usr/bin/env python3
# Zhihu-Hyperion - Free & Ad-Free Zhihu client for all platforms.
# Copyright (C) 2026, eltavine <me@eltavine.com>
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published by
# the Free Software Foundation (version 3 only).
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.

"""Vendors the Material Symbols listed in symbols.txt and generates AppIcons.kt.

material-icons-extended is frozen at 1.7.3; JetBrains recommends shipping Material Symbols as Compose resources instead.
Files come from google/material-design-icons at PINNED_COMMIT, so refreshing icons is an explicit, reviewable change:
bump the commit, run this script, review the diff.

    python3 core/icons/symbols.py          # download missing icons, drop unlisted ones, regenerate AppIcons.kt
    python3 core/icons/symbols.py --check  # fail if the committed files do not match symbols.txt (no network)
"""

import argparse
import concurrent.futures
import json
import pathlib
import re
import sys
import urllib.request

PINNED_COMMIT = "bd8cb85bd4bad964fe6918f79665bb40c3a8efef"
STYLE = "materialsymbolsrounded"
MODULE = pathlib.Path(__file__).resolve().parent
MANIFEST = MODULE / "symbols.txt"
DRAWABLES = MODULE / "src/commonMain/composeResources/drawable"
KOTLIN = MODULE / "src/commonMain/kotlin/com/github/zly2006/zhihu/icons/AppIcons.kt"
RES_PACKAGE = "zhihu.core.icons.generated.resources"
# The licenses screen lists Maven dependencies only; vendored icons need their own AboutLibraries entry.
LIBRARY_DEFINITION = MODULE.parents[1] / "aboutlibraries/libraries/material-symbols.json"
LICENSE_HEADER = (MODULE / "src/commonMain/kotlin/com/github/zly2006/zhihu/icons/AppIcon.kt").read_text(encoding="utf-8")
LICENSE_HEADER = LICENSE_HEADER[: LICENSE_HEADER.index("*/") + 2]


def read_manifest() -> list[tuple[str, str, bool]]:
    """Returns (token, resource file name, fill) for every icon, sorted by token."""
    icons: dict[str, tuple[str, str, bool]] = {}
    for line_number, raw in enumerate(MANIFEST.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw.split("#", 1)[0].strip()
        if not line:
            continue
        symbol, *flags = line.split()
        if not re.fullmatch(r"[a-z0-9_]+", symbol) or not set(flags) <= {"filled", "solid"} or len(flags) > 1:
            sys.exit(f"symbols.txt:{line_number}: cannot parse {raw!r}")
        fill = bool(flags)
        token = "".join(part.capitalize() for part in symbol.split("_")) + ("Filled" if flags == ["filled"] else "")
        if token in icons:
            sys.exit(f"symbols.txt:{line_number}: {token} is listed twice")
        icons[token] = (token, symbol + ("_filled" if fill else ""), fill)
    return sorted(icons.values())


def download(file: str, fill: bool) -> str:
    symbol = file.removesuffix("_filled") if fill else file
    variant = "_fill1_24px" if fill else "_24px"
    url = (
        f"https://raw.githubusercontent.com/google/material-design-icons/{PINNED_COMMIT}"
        f"/symbols/android/{symbol}/{STYLE}/{symbol}{variant}.xml"
    )
    try:
        with urllib.request.urlopen(url, timeout=60) as response:
            xml = response.read().decode("utf-8")
    except OSError as error:
        sys.exit(f"{url}: {error}")
    # Compose resources resolve neither Android theme attributes nor framework colors: the icon is tinted by Icon().
    xml = re.sub(r'\s+android:tint="[^"]*"', "", xml)
    xml = xml.replace('android:fillColor="@android:color/white"', 'android:fillColor="#000000"')
    if "?attr/" in xml or "@android:" in xml:
        sys.exit(f"{url} references Android resources that Compose resources cannot resolve")
    return xml


def generate_kotlin(icons: list[tuple[str, str, bool]]) -> str:
    imports = "\n".join(f"import {RES_PACKAGE}.{name}" for name in sorted({"Res", *(file for _, file, _ in icons)}))
    properties = "\n".join(f"    val {token} = AppIcon(Res.drawable.{file})" for token, file, _ in icons)
    return f"""{LICENSE_HEADER}

// 图标清单在 symbols.txt 中维护；修改后运行 core/icons/symbols.py 同步资源和声明。

package com.github.zly2006.zhihu.icons

{imports}

/**
 * 应用用到的全部图标，来自 Material Symbols Rounded。`*Filled` 是选中、已点赞、已收藏等状态用的填充版本。
 * 新增图标先在 core/icons/symbols.txt 登记，再运行 core/icons/symbols.py。
 */
object AppIcons {{
{properties}
}}
"""


def generate_library_definition() -> str:
    definition = {
        "uniqueId": "vendored:material-symbols",
        "artifactVersion": PINNED_COMMIT[:7],
        "name": "Material Symbols",
        "description": "Google's Material Symbols (Rounded style), vendored as vector resources in :core:icons.",
        "website": "https://fonts.google.com/icons",
        "developers": [{"name": "Google", "organisationUrl": "https://github.com/google/material-design-icons"}],
        "scm": {"url": "https://github.com/google/material-design-icons"},
        "licenses": ["Apache-2.0"],
    }
    return json.dumps(definition, ensure_ascii=False, indent=2) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="verify committed files instead of updating them")
    args = parser.parse_args()

    icons = read_manifest()
    wanted = {f"{file}.xml": fill for _, file, fill in icons}
    present = {path.name for path in DRAWABLES.glob("*.xml")}
    kotlin = generate_kotlin(icons)
    library_definition = generate_library_definition()

    if args.check:
        problems = [f"missing {name}" for name in sorted(wanted.keys() - present)]
        problems += [f"not in symbols.txt: {name}" for name in sorted(present - wanted.keys())]
        if not KOTLIN.exists() or KOTLIN.read_text(encoding="utf-8") != kotlin:
            problems.append(f"{KOTLIN.relative_to(MODULE)} is stale")
        if not LIBRARY_DEFINITION.exists() or LIBRARY_DEFINITION.read_text(encoding="utf-8") != library_definition:
            problems.append(f"{LIBRARY_DEFINITION.name} is stale")
        if problems:
            sys.exit("Icons are out of date; run core/icons/symbols.py:\n  " + "\n  ".join(problems))
        return

    DRAWABLES.mkdir(parents=True, exist_ok=True)
    for name in present - wanted.keys():
        (DRAWABLES / name).unlink()
    missing = sorted(wanted.keys() - present)
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        contents = pool.map(lambda name: download(name.removesuffix(".xml"), wanted[name]), missing)
        for name, xml in zip(missing, contents):
            (DRAWABLES / name).write_text(xml, encoding="utf-8")
    KOTLIN.write_text(kotlin, encoding="utf-8")
    LIBRARY_DEFINITION.write_text(library_definition, encoding="utf-8")
    print(f"{len(icons)} icons, {len(missing)} downloaded")


if __name__ == "__main__":
    main()
