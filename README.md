# Demo for SQL Transfer

This is a demo for SQL Transfer. It is a simpfied tool that can be used to transfer SQL statements between two DBMS.

## How to Use

This project can only be run in Linux. Here is using Ubuntu 20.04 as an example.

### Compile

Install Java JDK and Maven:
```bash
sudo apt install openjdk-8-jdk
sudo apt install maven
```

Compile the project:
```bash
mvn install
```

If you want to add other JDBC drivers, please modify the `pom.xml` file.

### Capture Sub-Schema

Using PostgreSQL as an example. Start a PostgreSQL instance via docker:

```bash
docker run --name some-postgres -p 5432:5432 -e POSTGRES_PASSWORD=mysecretpassword -d postgres:15.1
```

Run a SQL statement:
```bash
java -cp ./target/Sedar-1.0-SNAPSHOT.jar com.capture.JdbcHandler \
    "org.postgresql.Driver" "jdbc:postgresql://172.17.0.1:5432/postgres" "postgres" "mysecretpassword" \
    "CREATE TABLE x (y INT)"
```

It will output in STDIN like this:
```sql
CREATE TABLE x (y INT);
-- Note that "x" is a TABLE, "y" is a column with int4 type
```

This information can be used to transfer the SQL statement to another DBMS.

### SQL Transfer

When you want to transfer a single SQL statement, you can use the following script in path `src/main/resources/scripts/chatgpt_convert_v2.sh`.

```bash
export OPENAI_API_KEY="your_openai_api_key"
./src/main/resources/scripts/chatgpt_convert_v2.sh "CREATE TABLE x (y INT);
-- Note that "x" is a TABLE, "y" is a column with int4 type" "PostgreSQL" "MonetDB"
```

The command above can transfer the SQL statement from PostgreSQL to MonetDB.
