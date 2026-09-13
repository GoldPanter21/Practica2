from flask import Flask, jsonify, request
from flask_sqlalchemy import SQLAlchemy
from flask_bcrypt import Bcrypt
import os

app = Flask(__name__)

# 1. Configuración de la Base de Datos (SQLite)
# El archivo se guardará en la carpeta del contenedor como 'site.db'
app.config['SQLALCHEMY_DATABASE_URI'] = 'sqlite:///site.db'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

db = SQLAlchemy(app)
bcrypt = Bcrypt(app)

# 2. Modelo de Usuario (La tabla en la BD)
class User(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    username = db.Column(db.String(20), unique=True, nullable=False)
    password = db.Column(db.String(60), nullable=False)
    role = db.Column(db.String(20), nullable=False, default='usuario') # Nueva columna

    def __repr__(self):
        return f"User('{self.username}', '{self.role}')"

# 3. Rutas

@app.route('/')
def hello():
    return jsonify({"message": "API Funcionando"})

# Endpoint de REGISTRO
@app.route('/register', methods=['POST'])
def register():
    data = request.get_json()
    username = data.get('username')
    password = data.get('password')

    # Verificar si el usuario ya existe
    if User.query.filter_by(username=username).first():
        return jsonify({"message": "El usuario ya existe"}), 400

    # Encriptar contraseña
    hashed_password = bcrypt.generate_password_hash(password).decode('utf-8')
    
    # Crear y guardar nuevo usuario
    new_user = User(username=username, password=hashed_password, role='usuario')
    db.session.add(new_user)
    db.session.commit()

    return jsonify({"message": "Usuario creado exitosamente"}), 201

# Endpoint de LOGIN
@app.route('/login', methods=['POST'])
def login():
    data = request.get_json()
    username = data.get('username')
    password = data.get('password')

    user = User.query.filter_by(username=username).first()

    # Verificamos si el usuario existe y si la contraseña coincide con el hash
    if user and bcrypt.check_password_hash(user.password, password):
        return jsonify({
            "status": "success",
            "message": "Login exitoso",
            "user_id": user.id,
            "username": user.username,
            "role": user.role # Nueva línea agregada para el rol
        }), 200
    else:
        return jsonify({"status": "error", "message": "Credenciales inválidas"}), 401


# 4. Operaciones CRUD para Usuarios

# GET: Leer todos los usuarios registrados
@app.route('/users', methods=['GET'])
def get_users():
    users = User.query.all()
    # Retornamos los usuarios en forma de lista de diccionarios
    # Se omite la contraseña por seguridad
    users_list = [{"id": u.id, "username": u.username} for u in users]
    return jsonify(users_list), 200

# PUT: Modificar un usuario por ID
@app.route('/users/<int:user_id>', methods=['PUT'])
def update_user(user_id):
    user = User.query.get(user_id)
    if not user:
        return jsonify({"message": "Usuario no encontrado"}), 404
    
    data = request.get_json()
    new_username = data.get('username')
    new_password = data.get('password')

    # Validar y actualizar el username si se envió uno nuevo
    if new_username:
        existing_user = User.query.filter_by(username=new_username).first()
        if existing_user and existing_user.id != user_id:
            return jsonify({"message": "El nombre de usuario ya está en uso"}), 400
        user.username = new_username

    # Encriptar y actualizar la contraseña si se envió una nueva
    if new_password:
        user.password = bcrypt.generate_password_hash(new_password).decode('utf-8')

    db.session.commit()
    return jsonify({"message": "Usuario actualizado exitosamente"}), 200

# DELETE: Eliminar un usuario por ID
@app.route('/users/<int:user_id>', methods=['DELETE'])
def delete_user(user_id):
    user = User.query.get(user_id)
    if not user:
        return jsonify({"message": "Usuario no encontrado"}), 404
    
    db.session.delete(user)
    db.session.commit()
    return jsonify({"message": "Usuario eliminado exitosamente"}), 200


# ==========================================
# EL ARRANQUE SIEMPRE DEBE IR HASTA EL FINAL
# ==========================================
if __name__ == '__main__':
    with app.app_context():
        db.create_all()
        
        # Inyección automática del primer administrador
        admin = User.query.filter_by(role='admin').first()
        if not admin:
            hash_admin = bcrypt.generate_password_hash('admin123').decode('utf-8')
            nuevo_admin = User(username='Admin', password=hash_admin, role='admin')
            db.session.add(nuevo_admin)
            db.session.commit()
    
    app.run(host='0.0.0.0', port=5000, debug=True)
    # Esto crea las tablas automáticamente si no existen al iniciar
    with app.app_context():
        db.create_all()
    
    app.run(host='0.0.0.0', port=5000, debug=True)