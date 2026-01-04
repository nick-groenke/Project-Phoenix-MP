package com.devil.phoenixproject.portal.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init() {
        // Railway provides these env vars for PostgreSQL
        val pgHost = System.getenv("PGHOST") ?: "localhost"
        val pgPort = System.getenv("PGPORT") ?: "5432"
        val pgDatabase = System.getenv("PGDATABASE") ?: "phoenix_portal"
        val pgUser = System.getenv("PGUSER") ?: "postgres"
        val pgPassword = System.getenv("PGPASSWORD") ?: "postgres"

        val jdbcUrl = "jdbc:postgresql://$pgHost:$pgPort/$pgDatabase"

        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            username = pgUser
            password = pgPassword
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

        Database.connect(HikariDataSource(config))

        transaction {
            SchemaUtils.createMissingTablesAndColumns(Users, WorkoutSessions, PersonalRecords)
        }
    }
}
