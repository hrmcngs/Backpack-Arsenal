#!/usr/bin/env python3
"""Generate the Soldier 11-style backpack model variants.

The empty and loaded variants intentionally share identical backpack geometry.
Only the loaded variant receives the vertically docked blade elements.
"""

import json
from copy import deepcopy
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ASSET_MODELS = ROOT / "src/main/resources/assets/backpack_arsenal/models/item"
EDIT_MODELS = ROOT / "model"

UV = {
    "metal": [0, 0, 5, 5],
    "edge": [5, 0, 10, 5],
    "dark": [5, 8, 10, 13],
    "orange": [12, 8, 16, 12],
    "glow": [12, 4, 16, 8],
    "warning": [12, 12, 16, 14],
    "blade": [0, 8, 5, 14],
}


def cube(name, start, end, material="metal", rotation=None, emissive=False):
    face = {"uv": UV[material], "texture": "#main"}
    if emissive:
        face["emissivity"] = 15
    result = {
        "name": name,
        "from": start,
        "to": end,
        "faces": {direction: deepcopy(face) for direction in
                  ("north", "east", "south", "west", "up", "down")},
    }
    if rotation:
        result["rotation"] = rotation
    return result


def rot(origin, axis, angle):
    return {"origin": origin, "axis": axis, "angle": angle}


BACKPACK = [
    # Thin rectangular military case seen in the supplied rear reference.
    cube("case", [2.0, 5.0, 5.8], [13.0, 18.0, 9.0]),
    cube("case_top", [3.0, 17.7, 5.4], [12.5, 19.0, 9.3], "edge"),
    cube("case_bottom", [2.5, 4.2, 5.4], [12.5, 5.5, 9.3], "edge"),
    cube("left_rail", [1.4, 5.5, 5.2], [2.6, 17.6, 9.4], "edge"),
    cube("right_rail", [12.4, 5.5, 5.2], [13.6, 17.6, 9.4], "edge"),
    cube("front_panel", [3.0, 6.0, 4.9], [12.0, 16.8, 5.9], "dark"),

    # Two orange circular-looking pressure/gas units on the left.
    cube("upper_port_bezel", [2.0, 12.6, 4.0], [5.8, 16.4, 5.3], "edge",
         rot([3.9, 14.5, 4.6], "z", 45)),
    cube("upper_port", [2.3, 12.9, 3.5], [5.5, 16.1, 4.4], "orange",
         rot([3.9, 14.5, 4.0], "z", 45), True),
    cube("upper_port_core", [2.8, 13.4, 3.2], [5.0, 15.6, 3.7], "glow", emissive=True),
    cube("lower_port_bezel", [2.0, 7.4, 4.0], [5.8, 11.2, 5.3], "edge",
         rot([3.9, 9.3, 4.6], "z", 45)),
    cube("lower_port", [2.3, 7.7, 3.5], [5.5, 10.9, 4.4], "orange",
         rot([3.9, 9.3, 4.0], "z", 45), True),
    cube("lower_port_core", [2.8, 8.2, 3.2], [5.0, 10.4, 3.7], "glow", emissive=True),

    # Central X-bracing and regulator block.
    cube("brace_down", [6.8, 6.8, 4.1], [8.0, 15.4, 4.9], "edge",
         rot([7.4, 11.1, 4.5], "z", 45)),
    cube("brace_up", [6.8, 6.8, 4.0], [8.0, 15.4, 4.8], "edge",
         rot([7.4, 11.1, 4.4], "z", -45)),
    cube("regulator", [6.1, 9.7, 3.4], [9.7, 12.8, 4.4], "metal"),
    cube("regulator_slot", [7.0, 10.5, 3.0], [8.8, 12.0, 3.6], "dark"),

    # Yellow warning marking and corner locks.
    cube("warning_plate", [6.5, 14.2, 3.8], [10.2, 16.4, 4.4], "warning", emissive=True),
    cube("top_left_lock", [2.0, 17.3, 4.5], [3.1, 18.5, 5.8], "orange"),
    cube("bottom_right_lock", [11.9, 4.7, 4.5], [13.2, 6.2, 5.8], "orange"),

    # The sword holder is fixed vertically to the backpack's right side.
    cube("holder_spine", [13.1, 6.0, 6.0], [15.2, 17.8, 9.2], "edge"),
    cube("holder_upper_clamp", [12.7, 15.4, 5.2], [15.8, 17.2, 9.8], "orange"),
    cube("holder_lower_clamp", [12.8, 6.1, 5.3], [15.7, 7.8, 9.7], "orange"),
]


BLADE = [
    # Image reference: grip above the shoulder, straight blade well below the pack.
    cube("blade", [13.55, -7.0, 6.6], [15.15, 15.8, 8.1], "blade"),
    cube("blade_edge", [15.15, -7.0, 6.7], [15.55, 15.8, 7.9], "edge"),
    cube("blade_tip", [13.75, -8.5, 6.7], [15.15, -7.0, 8.0], "blade",
         rot([14.45, -7.0, 7.35], "z", 45)),
    cube("guard", [12.7, 16.2, 6.0], [16.0, 17.3, 8.8], "orange"),
    cube("grip", [13.45, 17.0, 6.5], [15.25, 23.2, 8.3], "dark"),
    cube("grip_cut_1", [13.2, 18.2, 6.2], [15.5, 19.0, 8.6], "edge"),
    cube("grip_cut_2", [13.2, 20.3, 6.2], [15.5, 21.1, 8.6], "edge"),
    cube("pommel", [13.0, 22.8, 6.1], [15.7, 24.2, 8.7], "dark"),
]


DISPLAY = {
    "thirdperson_righthand": {"rotation": [85, -90, 0], "translation": [0, -2, -6.75], "scale": [0.7, 0.7, 0.7]},
    "thirdperson_lefthand": {"rotation": [85, -90, 0], "translation": [0, -2, -6.75], "scale": [0.7, 0.7, 0.7]},
    "firstperson_righthand": {"scale": [0.5, 0.5, 0.5]},
    "firstperson_lefthand": {"scale": [0.5, 0.5, 0.5]},
    "ground": {"translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
    "gui": {"rotation": [25, -150, 0], "translation": [0, -1, 0], "scale": [0.65, 0.65, 0.65]},
    "fixed": {"translation": [0, -1.25, 0.5], "scale": [0.6, 0.6, 0.6]},
    "sophisticatedbackpacks:worn": {"translation": [0, -1.25, 0.5], "scale": [0.6, 0.6, 0.6]},
    "the_four_primitives_and_weapons:back": {"rotation": [0, -180, -180], "translation": [0, 0, 7.25], "scale": [1.05, 1.05, 1.05]},
    "backpack_arsenal:chestplate": {"translation": [0, -2, 0], "scale": [0.7, 0.7, 0.7]},
    "backpack_arsenal:placed": {"translation": [0, 5, 0], "scale": [0.65, 0.65, 0.65]},
}


def model(elements, credit):
    return {
        "credit": credit,
        "texture_size": [64, 64],
        "textures": {
            "main": "backpack_arsenal:item/arsenal_backpack_fire",
            "particle": "backpack_arsenal:item/arsenal_backpack_fire",
        },
        "elements": elements,
        "display": DISPLAY,
    }


def write(name, value):
    text = json.dumps(value, ensure_ascii=False, indent=2) + "\n"
    (ASSET_MODELS / f"{name}.json").write_text(text, encoding="utf-8")
    (EDIT_MODELS / f"{name}.json").write_text(text, encoding="utf-8")


write("arsenal_backpack_fire", model(BACKPACK, "Soldier 11 fire backpack - empty holder"))
write("arsenal_backpack_fire_loaded", model(BACKPACK + BLADE, "Soldier 11 fire backpack - blade docked"))
write("fire_blade", model(BLADE, "Soldier 11 fire blade"))
