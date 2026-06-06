#!/usr/bin/env python3
"""
Blockbench で voltaic_blade.json を編集・保存した後、これを実行すると
Bedrock 形式 (多軸回転 + format_version) を Java Block/Item 互換に自動変換する。

多軸回転の扱い (見た目を保つ順):
  1. 90/180/270° 系は座標スワップで ジオメトリに吸収 (face 名も回転、見た目維持)
  2. 残った非 90° 軸が 1 つで Java valid angle (±45/±22.5/0) なら単軸 rotation
  3. それでも残れば最終手段で AABB ベイク (見た目崩れる)

使い方:
    python3 tools/fix_voltaic_model.py
"""
import json, math, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TARGET = os.path.join(ROOT,
    'src/main/resources/assets/backpack_arsenal/models/custom/voltaic_blade.json')

JV = [-45, -22.5, 0, 22.5, 45]
SNAP_90 = {180, 90, -90, -180, 270, -270}

# axis 90° CCW 回転で face 名がどう変わるか
FACE_ROT_X = {'north':'down','down':'south','south':'up','up':'north','east':'east','west':'west'}
FACE_ROT_Y = {'north':'west','west':'south','south':'east','east':'north','up':'up','down':'down'}
FACE_ROT_Z = {'east':'up','up':'west','west':'down','down':'east','north':'north','south':'south'}

def rotate_coord_90(coord, axis, n):
    for _ in range(n):
        x, y, z = coord
        if axis == 0:   coord = [x, -z, y]
        elif axis == 1: coord = [z, y, -x]
        else:           coord = [-y, x, z]
    return coord

def apply_90(el, axis, angle, origin):
    n = (int(angle) // 90) % 4
    if n == 0: return False
    cx, cy, cz = origin
    def rot(p):
        rel = [p[0]-cx, p[1]-cy, p[2]-cz]
        rr = rotate_coord_90(rel, axis, n)
        return [rr[0]+cx, rr[1]+cy, rr[2]+cz]
    p1 = rot(el['from']); p2 = rot(el['to'])
    el['from'] = [min(p1[i], p2[i]) for i in range(3)]
    el['to']   = [max(p1[i], p2[i]) for i in range(3)]
    face_map = [FACE_ROT_X, FACE_ROT_Y, FACE_ROT_Z][axis]
    new_faces = {}
    for fname, fdata in el.get('faces', {}).items():
        nm = fname
        for _ in range(n):
            nm = face_map.get(nm, nm)
        new_faces[nm] = fdata
    el['faces'] = new_faces
    return True

def rotate_point(p, ai, deg, o):
    rad = math.radians(deg); c, s = math.cos(rad), math.sin(rad)
    dx, dy, dz = p[0]-o[0], p[1]-o[1], p[2]-o[2]
    if ai == 0:   return [dx+o[0], (dy*c - dz*s)+o[1], (dy*s + dz*c)+o[2]]
    elif ai == 1: return [(dx*c + dz*s)+o[0], dy+o[1], (-dx*s + dz*c)+o[2]]
    else:         return [(dx*c - dy*s)+o[0], (dx*s + dy*c)+o[1], dz+o[2]]

def aabb_after(fp, tp, axes, o):
    pts = []
    for fx in [fp[0], tp[0]]:
        for fy in [fp[1], tp[1]]:
            for fz in [fp[2], tp[2]]:
                pp = [fx, fy, fz]
                for ai, ad in axes:
                    pp = rotate_point(pp, ai, ad, o)
                pts.append(pp)
    return [min(p[i] for p in pts) for i in range(3)], [max(p[i] for p in pts) for i in range(3)]

with open(TARGET) as f:
    m = json.load(f)

absorbed = baked = single = 0
for el in m.get('elements', []):
    r = el.get('rotation')
    if not r or 'angle' in r: continue
    o = r.get('origin', [8, 8, 8])
    angles = {a: r.get(a, 0) for a in 'xyz'}

    # Step 1: 90° 系をジオメトリに吸収
    for ax_name, ax_idx in [('x', 0), ('y', 1), ('z', 2)]:
        ang = angles[ax_name]
        if ang in SNAP_90:
            if apply_90(el, ax_idx, ang, o):
                angles[ax_name] = 0
                absorbed += 1

    # Step 2: 残りを判定
    nz = [(i, angles['xyz'[i]]) for i in range(3) if angles['xyz'[i]] != 0]
    if not nz:
        el.pop('rotation', None); continue
    if len(nz) == 1 and nz[0][1] in JV:
        ai, ag = nz[0]
        el['rotation'] = {'angle': ag, 'axis': 'xyz'[ai], 'origin': o}
        single += 1
    else:
        mins, maxs = aabb_after(el['from'], el['to'], nz, o)
        el['from'] = mins; el['to'] = maxs
        el.pop('rotation', None); baked += 1

m.pop('format_version', None)

with open(TARGET, 'w') as f:
    json.dump(m, f, indent='\t')

print(f"voltaic_blade.json: absorbed_90={absorbed} single_axis={single} baked={baked} elements={len(m['elements'])}")
