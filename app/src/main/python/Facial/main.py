import tkinter as tk
from tkinter import ttk
import subprocess
from PIL import ImageTk, Image
import capturandoRostros

def agregar_usuario(nombre_usuario):
    capturandoRostros.rostro(nombre_usuario)

def entrenar():  # Modificar la función entrenar
    subprocess.Popen(["python", "entrenandoRF.py"])

def login():
    subprocess.Popen(["python", "ReconocimientoFacial.py"])

def cerrar_ventana():
    ventana.destroy()

def emociones():
    subprocess.Popen(["python", "FaceEmotionVideo.py"])

ventana = tk.Tk()
ventana.title("Reconocimiento Facial")
ventana.geometry("600x400")

def centrar_ventana():
    ventana.update_idletasks()
    ancho = ventana.winfo_width()
    alto = ventana.winfo_height()
    x = (ventana.winfo_screenwidth() // 2) - (ancho // 2)
    y = (ventana.winfo_screenheight() // 2) - (alto // 2)
    ventana.geometry(f'{ancho}x{alto}+{x}+{y}')

centrar_ventana()

style = ttk.Style()
style.configure('TButton', font=('Arial', 12), padding=10, background='#2196F3', foreground='black')
style.map('TButton', background=[('active', '#1E88E5'), ('!active', '#2196F3')])

def create_button(parent, text, command):
    button = ttk.Button(parent, text=text, command=command, style='TButton')
    button.pack(pady=10, ipadx=10, ipady=5, fill='x')
    return button

imagen_original = Image.open("logo.png")
imagen_redimensionada = imagen_original.resize((ventana.winfo_width() // 2, ventana.winfo_height()), Image.BILINEAR)
imagen = ImageTk.PhotoImage(imagen_redimensionada)

canvas = tk.Canvas(ventana, width=ventana.winfo_width() // 2, height=ventana.winfo_height())
canvas.pack(side=tk.LEFT, fill=tk.Y)
canvas.create_image(0, 0, anchor=tk.NW, image=imagen)

frame_widgets = tk.Frame(ventana)
frame_widgets.pack(side=tk.RIGHT, fill=tk.BOTH, expand=True, padx=20, pady=20)

entry = tk.Entry(frame_widgets, width=30, font=('Arial', 12))
entry.pack(pady=10, ipadx=10, ipady=5, fill='x')
entry.insert(0, 'Ingrese nombre de Usuario')  # Placeholder

def on_entry_click(event):
    if entry.get() == 'Ingrese nombre de Usuario':
        entry.delete(0, tk.END)
        entry.config(fg='black')

def on_focusout(event):
    if not entry.get():
        entry.insert(0, 'Ingrese nombre de Usuario')
        entry.config(fg='grey')

entry.bind('<FocusIn>', on_entry_click)
entry.bind('<FocusOut>', on_focusout)

frame_botones = tk.Frame(frame_widgets)
frame_botones.pack(pady=5, fill='both', expand=True)

btn_agregar_usuario = create_button(frame_botones, "Agregar Usuario",lambda: agregar_usuario(entry.get()))

btn_login = create_button(frame_botones, "Login",login)

btn_emociones = create_button(frame_botones, "Emociones", emociones)
btn_salir = create_button(frame_botones, "Salir", cerrar_ventana)

def actualizar_imagen(event):
    nuevo_ancho = ventana.winfo_width() // 2
    nuevo_alto = ventana.winfo_height()
    imagen_redimensionada = imagen_original.resize((nuevo_ancho, nuevo_alto), Image.BILINEAR)
    imagen_actualizada = ImageTk.PhotoImage(imagen_redimensionada)
    canvas.config(width=nuevo_ancho, height=nuevo_alto)
    canvas.create_image(0, 0, anchor=tk.NW, image=imagen_actualizada)
    canvas.image = imagen_actualizada

ventana.bind('<Configure>', actualizar_imagen)

ventana.mainloop()