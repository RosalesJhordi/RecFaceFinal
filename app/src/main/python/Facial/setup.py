from cx_Freeze import setup, Executable

setup(
    name="RecFacial",
    version="0.1",
    description="Reconocimiento facial",
    executables=[Executable("main.py")]
)
