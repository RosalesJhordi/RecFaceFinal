import cv2
import numpy as np

def model(image_path, username):
    # Inicializar el detector de rostros
    face_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_frontalface_default.xml')
    
    faces_data = []
    labels = []

    # Procesar cada imagen para obtener los rostros y las etiquetas
    for path, username in zip(image_path, username):
        # Cargar la imagen y convertirla a escala de grises
        image = cv2.imread(path)
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        
        # Detectar rostros en la imagen
        faces = face_cascade.detectMultiScale(gray, scaleFactor=1.3, minNeighbors=5)

        for (x, y, w, h) in faces:
            # Recortar el rostro de la imagen y agregarlo a los datos de rostros
            face = gray[y:y+h, x:x+w]
            faces_data.append(face)
            labels.append(username)

    # Entrenar el modelo de reconocimiento facial
    recognizer = cv2.face.EigenFaceRecognizer_create()
    recognizer.train(faces_data, np.array(labels))

    # Guardar el modelo entrenado en el sistema de archivos
    recognizer.save("trained_model.xml")
    return "Entrenamiento completado"