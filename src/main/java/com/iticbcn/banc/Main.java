package com.iticbcn.banc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool.PoolInitializationException;

public class Main {

    
    public static void main(String[] args) {
        Properties p = new Properties();

        try {
            p.load(Files.newBufferedReader(
                Path.of("banc", "src", "main", "resources", "conn.properties")
            ));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        HikariConfig hkcfg = new HikariConfig();

        // Parametria de la connexió
        hkcfg.setJdbcUrl(p.getProperty("DB_URL"));
        hkcfg.setUsername(p.getProperty("DB_USER"));
        hkcfg.setPassword(p.getProperty("DB_PASSWORD"));
        hkcfg.setMaximumPoolSize(Integer.parseInt(p.getProperty("DB_MAX_POOL", "5")));

        try (HikariDataSource hkds = new HikariDataSource(hkcfg)) {
            // Obtenim una única connexió
            try (Connection cnn = hkds.getConnection()) {
                System.out.println("Connectat!");
                String query = "SELECT * FROM statements";
                try (PreparedStatement ps = cnn.prepareStatement(query);
                    ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String resultat = String.join(";",
                            rs.getString("id_statement"),
                            rs.getString("tipus_moviment"),
                            rs.getString("import"),
                            rs.getString("divisa")
                        );
                        System.out.println("moviment amb id: " + resultat);
                    }
                }

                for (String resultat : readAllStatements(cnn)) {
                    System.out.println("moviment amb id: " + resultat);
                }
                
                Scanner sc = new Scanner(System.in);
                System.out.println("Quantitat:");
                Double quantitat = sc.nextDouble();
                sc.nextLine();
                for (String resultat : readStatementsByImport(cnn, quantitat)) {
                    System.out.println(resultat);
                }

                List<String> userData  = new ArrayList<>();
                System.out.println("Nom:");
                String nom = sc.nextLine();
                userData.add(nom);
                System.out.println("Cognoms:");
                String cognoms = sc.nextLine();
                userData.add(cognoms);
                System.out.println("DNI:");
                String dni = sc.nextLine();
                userData.add(dni);
                System.out.println(addUser(cnn, userData));
                
            } catch (SQLException e) {
                System.out.println("No connectat!");
                e.printStackTrace();
            }
        } catch (PoolInitializationException e) {
            System.out.println(
                "No s'ha pogut connectar a la base de dades: " + e.getMessage()
            );
        }
    }


    public static List<String> readAllStatements (Connection conn) throws SQLException {
        List<String> resultatQuery = new ArrayList<String>();
        String query = "SELECT * FROM statements";
        PreparedStatement ps = conn.prepareStatement(query);
       
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String resultat = String.join(";",rs.getString("id_statement"),
                        rs.getString("tipus_moviment"),
                        rs.getString("import"),
                        rs.getString("divisa"));
                resultatQuery.add(resultat);
            }
        }
        return resultatQuery;
    }

    public static List<String> readStatementsByImport(Connection conn, double quantitat) throws SQLException {
        List<String> resultatQuery = new ArrayList<String>();
        String query = "SELECT * FROM statements WHERE import >= ?";
        
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setDouble(1, quantitat);

            try (ResultSet rs = ps.executeQuery()) {
                // Obtenim les metadades
                ResultSetMetaData meta = rs.getMetaData();
                // Construïm la capçalera
                String header = String.join(";",
                        meta.getColumnName(1),
                        meta.getColumnName(3),
                        meta.getColumnName(8),
                        meta.getColumnName(10));
                resultatQuery.add(header);
                // Recorrem els resultats
                while (rs.next()) {
                    String resultat = String.join(";",
                            rs.getString(1),
                            rs.getString(3),
                            rs.getString(8),
                            rs.getString(10));
                    resultatQuery.add(resultat);
                }
            }
        }
        return resultatQuery;
    }

    public static boolean userExists(Connection conn, List<String> userData) throws SQLException {
        String query = "SELECT * FROM clients WHERE id_fiscal = ?";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, userData.get(2));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static boolean addUser (Connection conn, List<String> userData) throws SQLException {
        if (userExists(conn, userData)) {
            return false;
        }

        String query = "INSERT INTO clients (nom, cognoms, id_fiscal) VALUES (?, ?, ?)";
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, userData.get(0));
                ps.setString(2, userData.get(1));
                ps.setString(3, userData.get(2));
                
                int rows = ps.executeUpdate();

                if (rows == 1) {
                    conn.commit();
                    return true;
                } else {
                    conn.rollback();
                    return false;
                }

            }
        } catch (SQLException e) {
            conn.rollback();
            throw e;

        } finally {
            conn.setAutoCommit(true);
        }

    }

}