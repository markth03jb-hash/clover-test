"""Build a traced weapon (from tools/blender/trace_prep.py's data) in Blender, render it, and export it as an OBJ for
Forge's OBJ loader. Run with Blender's Python (bpy).

  python tools/blender/trace_build.py DATA_DIR render     previews (front, angled, back, edge, close-up)
  python tools/blender/trace_build.py DATA_DIR game       writes it into the mod (models/item, textures/item)

The surface is the picture on both faces; a second, glowing layer (lit by its own material: Ka 1) lies just over the
triangles that carry red, its texture transparent everywhere but the red - so the red parts glow.
"""
import json, math, os, sys
import bpy, bmesh

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gilded_thorn as gt


def build(data_dir):
    D = json.load(open(os.path.join(data_dir, "mesh.json")))
    name = D["name"]
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bm = bmesh.new()
    uvl = bm.loops.layers.uv.new("UVMap")
    nb = D["boundary"]
    front = [bm.verts.new((x, y, z)) for x, y, z in D["verts"]]
    back = [front[i] if i < nb else bm.verts.new((x, -y, z)) for i, (x, y, z) in enumerate(D["verts"])]
    LIFT = 0.05
    over_f = [bm.verts.new((x, y + LIFT, z)) for x, y, z in D["verts"]]
    over_b = [bm.verts.new((x, -y - LIFT, z)) for x, y, z in D["verts"]]
    uvs = D["uvs"]
    red = set(D["red_tris"])

    def tri(vs, idx, want_pos_y, mat):
        try: f = bm.faces.new([vs[i] for i in idx])
        except ValueError: return
        f.normal_update()
        if (f.normal.y > 0) != want_pos_y:
            f.normal_flip()
        for lp in f.loops:
            i = vs.index(lp.vert) if False else None
        f.material_index = mat
        return f

    for ti, t in enumerate(D["tris"]):
        for vs, pos, mat in ((front, True, 0), (back, False, 0)) + (((over_f, True, 1), (over_b, False, 1)) if ti in red else ()):
            f = None
            try: f = bm.faces.new([vs[i] for i in t])
            except ValueError: continue
            # wind it so it faces outward (+Y on the front, -Y on the back)
            a, b, c = [v.co for v in f.verts]
            ny = (b - a).cross(c - a).y
            if (ny > 0) != pos: f.normal_flip()
            f.material_index = mat
            lookup = {id(vs[i]): i for i in t}
            for lp in f.loops: lp[uvl].uv = uvs[lookup[id(lp.vert)]]
    bm.verts.ensure_lookup_table()
    loose = [v for v in bm.verts if not v.link_faces]
    bmesh.ops.delete(bm, geom=loose, context="VERTS")
    me = bpy.data.meshes.new(name); bm.to_mesh(me); bm.free()
    for p in me.polygons: p.use_smooth = True
    ob = bpy.data.objects.new(name, me); bpy.context.collection.objects.link(ob)

    def material(mname, tex, glow):
        m = bpy.data.materials.new(mname); m.use_nodes = True
        nt = m.node_tree; bsdf = nt.nodes.get("Principled BSDF")
        img = nt.nodes.new("ShaderNodeTexImage"); img.image = bpy.data.images.load(tex); img.interpolation = "Closest"
        nt.links.new(img.outputs["Color"], bsdf.inputs["Base Color"]); bsdf.inputs["Roughness"].default_value = 0.8
        if glow:
            nt.links.new(img.outputs["Color"], bsdf.inputs["Emission Color"]); bsdf.inputs["Emission Strength"].default_value = 1.0
            nt.links.new(img.outputs["Alpha"], bsdf.inputs["Alpha"])
        return m
    me.materials.append(material(name, os.path.join(data_dir, name + ".png"), False))
    me.materials.append(material(name + "_glow", os.path.join(data_dir, name + "_glow.png"), True))
    bpy.ops.object.select_all(action="DESELECT"); ob.select_set(True); bpy.context.view_layer.objects.active = ob
    ob.location = (8, -8, 8)
    bpy.ops.wm.obj_export(filepath=os.path.join(data_dir, name + ".obj"), export_selected_objects=True, export_uv=True,
                          export_normals=True, export_materials=True, export_triangulated_mesh=True,
                          forward_axis="NEGATIVE_Z", up_axis="Y", global_scale=1 / 16, path_mode="STRIP")
    print("tris", len(me.polygons))
    return ob, name


def to_game(data_dir, name):
    gt.export_to_game(name, data_dir, gt.DISPLAY, glowing={name + "_glow"}, textures={name + "_glow": name + "_glow"},
                      render_type="minecraft:cutout")   # cutout: the glowing layer is see-through everywhere but its red


if __name__ == "__main__":
    data_dir, mode = sys.argv[1], sys.argv[2]
    ob, name = build(data_dir)
    if mode == "render": gt.VIEW[:] = [40, 11.0]; gt.render(ob, data_dir)
    if mode == "game": to_game(data_dir, name)
