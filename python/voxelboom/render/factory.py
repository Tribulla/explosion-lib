

def make_viewer():
    try:
        from .pyvista_viewer import PyVistaViewer, _HAVE
        if _HAVE:
            return PyVistaViewer()
    except Exception:
        pass
    try:
        from .mpl_viewer import MatplotlibViewer, _HAVE
        if _HAVE:
            return MatplotlibViewer()
    except Exception:
        pass
    return None
