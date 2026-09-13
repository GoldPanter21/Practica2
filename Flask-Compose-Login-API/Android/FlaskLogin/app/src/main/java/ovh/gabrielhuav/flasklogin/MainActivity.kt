package ovh.gabrielhuav.flasklogin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ovh.gabrielhuav.flasklogin.ui.theme.FlaskLoginTheme
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.DELETE
import retrofit2.http.Path

data class UserItem(val id: Int, val username: String)
data class RegisterRequest(val username: String, val password: String)
data class RegisterResponse(val message: String)
data class LoginRequest(val username: String, val password: String)

data class LoginResponse(
    val status: String,
    val message: String,
    val user_id: Int?,
    val username: String?,
    val role: String?
)

interface ApiService {
    @GET("users")
    suspend fun getUsers(): List<UserItem>

    @POST("register")    suspend fun registerUser(@Body request: RegisterRequest): Response<RegisterResponse>

    @POST("login")
    suspend fun loginUser(@Body request: LoginRequest): Response<LoginResponse>

    // Nuevos endpoints para editar y borrar
    @PUT("users/{id}")
    suspend fun updateUser(@Path("id") id: Int, @Body request: RegisterRequest): Response<RegisterResponse>

    @DELETE("users/{id}")
    suspend fun deleteUser(@Path("id") id: Int): Response<RegisterResponse>
}

object RetrofitClient {
    private const val BASE_URL = "http://10.0.2.2:5000/" //10.0.2.2:5000

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlaskLoginTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        Navegador() // El motor de navegación inicia aquí
                    }
                }
            }
        }
    }
}

@Composable
fun Navegador() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LogIn(
                onNavigateToRegister = { navController.navigate("register") },
                onLoginAdmin = { navController.navigate("crud_home") },
                onLoginUser = { userId -> navController.navigate("user_home/$userId") }
            )
        }
        composable("register") {
            Registro(
                onNavigateToLogin = { navController.popBackStack() }
            )
        }
        composable("crud_home") {
            PanelAdmin(
                onLogout = {
                    navController.navigate("login") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        composable("user_home/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull() ?: 0
            Usuario(
                userId = userId,
                onLogout = {
                    navController.navigate("login") { popUpTo("login") { inclusive = true } }
                }
            )
        }
    }
}

@Composable
fun LogIn(onNavigateToRegister: () -> Unit, onLoginAdmin: () -> Unit, onLoginUser: (Int) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    var apiMessage by remember { mutableStateOf("") }
    var showApiAlert by remember { mutableStateOf(false) }
    var userRole by remember { mutableStateOf("") }
    var loggedUserId by remember { mutableStateOf(0) } // Variable para guardar el ID

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Bienvenido", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(bottom = 32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Usuario") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = "Mostrar contraseña")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                coroutineScope.launch {
                    try {
                        val requestData = LoginRequest(username = email, password = password)
                        val response = RetrofitClient.apiService.loginUser(requestData)

                        if (response.isSuccessful && response.body()?.status == "success") {
                            userRole = response.body()?.role ?: "usuario"
                            loggedUserId = response.body()?.user_id ?: 0 // Guardamos el ID extraído de Flask
                            apiMessage = "Bienvenido. Tu rol es: $userRole"
                            showApiAlert = true
                        } else {
                            apiMessage = "Credenciales incorrectas"
                            showApiAlert = true
                        }
                    } catch (e: Exception) {
                        apiMessage = "Fallo de red"
                        showApiAlert = true
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Iniciar Sesión")
        }

        if (showApiAlert) {
            AlertDialog(
                onDismissRequest = {
                    showApiAlert = false
                    if (userRole.lowercase() == "admin") onLoginAdmin()
                    else if (userRole.lowercase() == "usuario") onLoginUser(loggedUserId) // Enviamos el ID a la vista
                },
                title = { Text("Estado de Sesión") },
                text = { Text(apiMessage) },
                confirmButton = {
                    TextButton(onClick = {
                        showApiAlert = false
                        if (userRole.lowercase() == "admin") onLoginAdmin()
                        else if (userRole.lowercase() == "usuario") onLoginUser(loggedUserId)
                    }) {
                        Text("Aceptar")
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onNavigateToRegister) { Text("¿No tienes cuenta? Regístrate") }
    }
}

@Composable
fun Registro(onNavigateToLogin: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var showAlert by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var apiMessage by remember { mutableStateOf("") }
    var showApiAlert by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Crear Cuenta",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Usuario") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = "Alternar contraseña")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirmar Contraseña") },
            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (confirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                    Icon(imageVector = image, contentDescription = "Alternar confirmación")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (password != confirmPassword) {
                    showAlert = true
                } else {
                    // Petición HTTP POST
                    coroutineScope.launch {
                        try {
                            val requestData = RegisterRequest(username = email, password = password)
                            val response = RetrofitClient.apiService.registerUser(requestData)

                            if (response.isSuccessful) {
                                apiMessage = "Registro exitoso. Se te asignó el rol: Usuario."
                                showApiAlert = true
                            } else {
                                apiMessage = "Error: El usuario ya existe o datos inválidos."
                                showApiAlert = true
                            }
                        } catch (e: Exception) {
                            apiMessage = "Fallo de red: ${e.message}"
                            showApiAlert = true
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Registrarse")
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onNavigateToLogin) {
            Text("¿Ya tienes cuenta? Inicia sesión")
        }
    }

    if (showAlert) {
        AlertDialog(
            onDismissRequest = { showAlert = false },
            title = { Text("Contraseñas Diferentes") },
            text = { Text("Por favor, ingrese la misma contraseña en el campo de confirmación") },
            confirmButton = {
                TextButton(onClick = { showAlert = false }) {
                    Text("Aceptar")
                }
            }
        )
    }
    if (showApiAlert) {
        AlertDialog(
            onDismissRequest = {
                showApiAlert = false
                if (apiMessage.contains("exitoso")) onNavigateToLogin()
            },
            title = { Text("Aviso del Servidor") },
            text = { Text(apiMessage) },
            confirmButton = {
                TextButton(onClick = {
                    showApiAlert = false
                    if (apiMessage.contains("exitoso")) onNavigateToLogin()
                }) {
                    Text("Aceptar")
                }
            }
        )
    }
}

@Composable
fun PanelAdmin(onLogout: () -> Unit) {
    val userList = remember { mutableStateListOf<UserItem>() }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    // Variables para el diálogo de edición
    var selectedUser by remember { mutableStateOf<UserItem?>(null) }
    var editUsername by remember { mutableStateOf("") }
    var editPassword by remember { mutableStateOf("") }

    // Función para recargar la lista después de un cambio
    fun fetchUsers() {
        coroutineScope.launch {
            try {
                isLoading = true
                val response = RetrofitClient.apiService.getUsers()
                userList.clear()
                userList.addAll(response)
                isLoading = false
            } catch (e: Exception) {
                errorMessage = "Error de conexión: ${e.message}"
                isLoading = false
            }
        }
    }

    // Petición inicial al cargar la pantalla
    LaunchedEffect(Unit) {
        fetchUsers()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Panel CRUD (Admin)", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = onLogout) { Text("Salir") }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (errorMessage.isNotEmpty()) {
            Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Encabezados
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("ID", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(0.2f))
                    Text("Usuario", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(0.5f))
                    Text("Acción", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(0.3f))
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (userList.isEmpty()) {
                    Text("No hay registros disponibles", modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    userList.forEach { user ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(user.id.toString(), modifier = Modifier.weight(0.2f))
                            Text(user.username, modifier = Modifier.weight(0.5f))
                            TextButton(
                                onClick = {
                                    selectedUser = user
                                    editUsername = user.username
                                    editPassword = "" // Contraseña vacía por defecto
                                },
                                modifier = Modifier.weight(0.3f)
                            ) {
                                Text("Editar")
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }

    // Diálogo flotante para Editar o Borrar
    selectedUser?.let { user ->
        AlertDialog(
            onDismissRequest = { selectedUser = null },
            title = { Text("Editar Usuario ID: ${user.id}") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editUsername,
                        onValueChange = { editUsername = it },
                        label = { Text("Nuevo Usuario") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editPassword,
                        onValueChange = { editPassword = it },
                        label = { Text("Nueva Contraseña (Opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    coroutineScope.launch {
                        try {
                            val request = RegisterRequest(username = editUsername, password = editPassword)
                            val response = RetrofitClient.apiService.updateUser(user.id, request)
                            if (response.isSuccessful) {
                                selectedUser = null
                                fetchUsers() // Recargar la tabla
                            }
                        } catch (e: Exception) {
                            errorMessage = "Error al actualizar"
                        }
                    }
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val response = RetrofitClient.apiService.deleteUser(user.id)
                                if (response.isSuccessful) {
                                    selectedUser = null
                                    fetchUsers() // Recargar la tabla
                                }
                            } catch (e: Exception) {
                                errorMessage = "Error al borrar"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Borrar")
                }
            }
        )
    }
}

@Composable
fun Usuario(userId: Int, onLogout: () -> Unit) {
    var newUsername by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var apiMessage by remember { mutableStateOf("") }
    var showUpdateAlert by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Mi Perfil", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("ID de usuario: $userId", color = MaterialTheme.colorScheme.secondary)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = newUsername,
            onValueChange = { newUsername = it },
            label = { Text("Nuevo Nombre (Opcional)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = { Text("Nueva Contraseña (Opcional)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                coroutineScope.launch {
                    try {
                        val request = RegisterRequest(username = newUsername, password = newPassword)
                        val response = RetrofitClient.apiService.updateUser(userId, request)

                        if (response.isSuccessful) {
                            apiMessage = "¡Tus datos fueron actualizados!"
                            newUsername = ""
                            newPassword = ""
                        } else {
                            apiMessage = "Error: El nombre de usuario ya está ocupado."
                        }
                        showUpdateAlert = true
                    } catch (e: Exception) {
                        apiMessage = "Error de red al actualizar."
                        showUpdateAlert = true
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Actualizar mis datos")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cerrar Sesión")
        }
    }

    if (showUpdateAlert) {
        AlertDialog(
            onDismissRequest = { showUpdateAlert = false },
            title = { Text("Actualización") },
            text = { Text(apiMessage) },
            confirmButton = {
                TextButton(onClick = { showUpdateAlert = false }) { Text("Aceptar") }
            }
        )
    }
}