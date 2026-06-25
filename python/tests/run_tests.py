import importlib
import sys
import traceback
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

MODULES = ["test_render", "test_determinism", "test_gravity", "test_raycast",
           "test_shockwave", "test_trees"]


def main():
    sys.path.insert(0, str(ROOT / "tests"))
    passed = failed = 0
    for modname in MODULES:
        mod = importlib.import_module(modname)
        for name in dir(mod):
            if not name.startswith("test_"):
                continue
            fn = getattr(mod, name)
            if not callable(fn):
                continue
            try:
                fn()
                print(f"PASS  {modname}.{name}")
                passed += 1
            except Exception:
                print(f"FAIL  {modname}.{name}")
                traceback.print_exc()
                failed += 1
    print(f"\n{passed} passed, {failed} failed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
