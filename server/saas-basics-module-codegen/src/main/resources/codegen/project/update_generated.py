"""Apply a new generated bundle without replacing files edited by its developer."""
import argparse
import hashlib
import json
import os
import tempfile
from pathlib import Path


def digest(data):
    return hashlib.sha256(data).hexdigest()


def atomic_write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as out:
        out.write(data)
    os.replace(out.name, path)


def update(source, target, apply):
    source, target = source.resolve(), target.resolve()
    if source == target:
        raise ValueError("请指定上一份工程所在目录")
    old = json.loads((target/"source-lock.json").read_text())
    new = json.loads((source/"source-lock.json").read_text())
    previous, current = [json.loads((p/"project-design.json").read_text()) for p in (target,source)]
    if previous["code"] != current["code"] or current["revision"] < previous["revision"]:
        raise ValueError("应用编码或修订顺序不匹配")
    if previous != current and previous["revision"] == current["revision"]:
        raise ValueError("修改蓝图后请增加修订号")
    changes, conflicts = [], []
    for name in sorted(old.keys() | new.keys()):
        dest = (target / name).resolve()
        src = (source / name).resolve()
        if not dest.is_relative_to(target) or not src.is_relative_to(source) or name == "source-lock.json":
            raise ValueError("无效的生成路径")
        desired = src.read_bytes() if name in new else None
        if desired is not None and digest(desired) != new[name]:
            raise ValueError("新工程文件已被修改：" + name)
        actual = digest(dest.read_bytes()) if dest.is_file() else None
        if new.get(name) == old.get(name) or actual == new.get(name):
            continue
        if actual != old.get(name):
            conflicts.append(name)
        else:
            changes.append((dest,desired))
    if conflicts:
        raise ValueError("请先合并本地修改：" + ", ".join(conflicts))
    for path,data in changes:
        print(("更新 " if data is not None else "移除 ") + str(path.relative_to(target)))
    if apply:
        if current["revision"] > previous["revision"]:
            history = target / "revisions" / (str(previous["revision"])+".json")
            previous_data = (target/"project-design.json").read_bytes()
            if history.resolve().parent != (target/"revisions") or (history.exists() and history.read_bytes() != previous_data):
                raise ValueError("历史修订冲突")
            atomic_write(history, previous_data)
        for path,data in changes:
            if data is None:
                path.unlink()
            else:
                atomic_write(path,data)
        atomic_write(target/"source-lock.json", (source/"source-lock.json").read_bytes())


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("target", type=Path)
    parser.add_argument("--apply", action="store_true")
    opts = parser.parse_args()
    update(Path(__file__).resolve().parent,opts.target,opts.apply)
