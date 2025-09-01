# Cocos Challenge Backend

### Stack utilizado

* Java 17
* Project Reactor
* Spring Webflux
* Caffeine
* Gradle
* Docker
* Postman

### Como ejecutar y probar

* Utilizar algun IDE como IntelliJ o Eclipse, buildear el proyecto y ejecutarlo
* Utilizar el docker *(recomendado)*
  * ``` docker build -t cocos-challenge-backend:local .```
  * ``` 
     docker run --rm -p 8080:8080 \
    -e SPRING_R2DBC_URL="r2dbc:postgresql://ep-falling-mouse-a5gsvuuk-pooler.us-east-2.aws.neon.tech:5432/neondb?sslmode=require" \
    -e SPRING_R2DBC_USERNAME="neondb_owner" \
    -e SPRING_R2DBC_PASSWORD="npg_8ox5BzmUwvat" \
    cocos-challenge-backend:local 
    ```
* Utilizar la collection de Postman ubicada en /postman/cocos-challenge.postman_collection.json

### Consideraciones y licencias

* Se intento preservar el schema propuesto de la base, más alla de que existan oportunidades de mejora
* Particularmente se deja un script para crear algunos indices que mejoran la performance para la busqueda de instrumentos 
* Disponible en /src/main/resources/create_search_index.sql
* Se utilizo un cache para las búsquedas de instrumentos, el mismo es configurable via properties
* El mismo intenta evitar una sobrecarga en la base
* Los datos precargados de la base presentan inconsistencias (ej: hay mas sells que buys para BMA user 1)
* Se agrego una property que indica si se debe fallar o no al encontrar estos errores al momento de generar el portfolio balance
* Siempre se loggeara una alerta si se encuentra alguna inconsistencia: " Inconsistent trades for instrument BMA — skipping position"
* Se agregaron tests unitarios para los 3 servicios principales
* Se agrego la documentacion de los 3 servicios y metodos principales
* No se contemplaron posibles condiciones de carrera ni transaccionabilidad de las operaciones por considerarse fuera del scope del challenge




