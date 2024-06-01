import cv2
import os

dataPath = r'./Data'
imagePaths = os.listdir(dataPath)
print('imagePaths=',imagePaths)

face_recognizer = cv2.face.LBPHFaceRecognizer_create()
face_recognizer.read('modeloLBPHFace.xml')
cap = cv2.VideoCapture(0)

faceClassif = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_frontalface_default.xml')

while True:
    ret, frame = cap.read()
    if not ret:
        break

    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    auxFrame = gray.copy()

    faces = faceClassif.detectMultiScale(gray, 1.3, 5)

    for (x, y, w, h) in faces:

        rostro = auxFrame[y:y+h, x:x+w]
        rostro = cv2.resize(rostro, (150, 150), interpolation=cv2.INTER_CUBIC)

        result = face_recognizer.predict(rostro)

        cv2.rectangle(frame, (x, y), (x+w, y+h), (0, 255, 0), 2)

        if result[1] < 70:
            cv2.putText(frame, str(imagePaths[result[0]]), (x, y-25), cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 0), 2, cv2.LINE_AA)
            print("Acceso correcto: bienvenido: " + str(imagePaths[result[0]]))
        else:
            cv2.putText(frame, 'Desconocido', (x, y-25), cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 0, 255), 2, cv2.LINE_AA)


    cv2.imshow('frame', frame)

    if cv2.waitKey(1) == 27:
        break

    if cv2.getWindowProperty('frame', cv2.WND_PROP_VISIBLE) < 1:
        break

cap.release()
cv2.destroyAllWindows()
