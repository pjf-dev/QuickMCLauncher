package dev.heyitsskay.QuickMCLauncher;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import okhttp3.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Scanner;

public class Launch {

    /*

    java -jar QuickMCLauncher-1.0.jar %appdata%/.minecraft/versions/1.8.8/1.8.8.json

     */

    public static void main(String[] args) {

        Scanner s = new Scanner(System.in);

        System.out.println("Email: ");
        String email = s.nextLine();
        System.out.println("Password: ");
        String pass = s.nextLine();

        Account acc = null;
        try {
            acc = login(email, pass);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        System.out.println("Path To Version manifest: ");
        String vm = s.nextLine();

        try {
            startMC(getCommand(acc, vm));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String getCommand(Account account, String vManS) throws FileNotFoundException {
        File vMan = new File(vManS);
        if (vMan.exists()) {
            File libDir = new File(vMan.getAbsoluteFile().getParentFile().getParentFile().getParentFile().getPath() + File.separator + "libraries");
            ArrayList<String> libs = new ArrayList<>();
            ArrayList<String> natives = new ArrayList<>();
            Gson gson = new Gson();
            JsonObject vManJ = gson.fromJson(new FileReader(vMan), new TypeToken<JsonObject>() {
            }.getType());
            JsonArray libJ = vManJ.getAsJsonObject().getAsJsonArray("libraries");
            libJ.forEach(lE -> {
                JsonObject lib = lE.getAsJsonObject();
                if (lib.has("rules")) {
                    if (!lib.getAsJsonArray("rules").get(0).getAsJsonObject().has("os")
                            || (lib.getAsJsonArray("rules").get(0).getAsJsonObject().has("os")
                            && lib.getAsJsonArray("rules").get(0).getAsJsonObject().getAsJsonObject("os").get("name").getAsString().equals("windows"))) {
                        if (lib.getAsJsonObject("downloads").has("classifiers")) {
                            if (lib.getAsJsonObject("downloads").getAsJsonObject("classifiers").has("natives-windows")) {
                                natives.add(lib.getAsJsonObject("downloads").getAsJsonObject("classifiers").getAsJsonObject("natives-windows").get("path").getAsString());
                            } else {
                                natives.add(lib.getAsJsonObject("downloads").getAsJsonObject("classifiers").getAsJsonObject("natives-windows-64").get("path").getAsString());
                            }
                        } else {
                            libs.add(lib.getAsJsonObject("downloads").getAsJsonObject("artifact").get("path").getAsString());
                        }
                    }
                } else {
                    if (lib.getAsJsonObject("downloads").has("classifiers")) {
                        natives.add(lib.getAsJsonObject("downloads").getAsJsonObject("classifiers").getAsJsonObject("natives-windows").get("path").getAsString());
                    } else {
                        libs.add(lib.getAsJsonObject("downloads").getAsJsonObject("artifact").get("path").getAsString());
                    }
                }
            });
            File binDir = new File(libDir.getAbsoluteFile().getParentFile().getPath() + "\\bin");
            File vBinDir = new File(binDir.getAbsolutePath() + File.separator + vManJ.get("id").getAsString());
            if (!vBinDir.exists()) {
                vBinDir.mkdir();
                UnzipUtility unzipper = new UnzipUtility();
                for (String n : natives) {
                    try {
                        unzipper.unzip(libDir.getAbsolutePath() + File.separator + n, vBinDir.getAbsolutePath());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        System.exit(1);
                    }
                }
                File del = new File(vBinDir.getAbsolutePath() + File.separator + "META-INF");
                del.delete();
            }
            StringBuilder command = new StringBuilder();
            command.append("java -Xms512m -Xmx1g ").append("-Djava.library.path=\"").append(vBinDir.getPath()).append("\"").append(" -cp ");
            for (String lib : libs) {
                command.append(libDir.getPath()).append(File.separator).append(lib).append(";");
            }
            command.append(vMan.getParentFile().getPath()).append(File.separator).append(vMan.getName().replace(".json", ".jar")).append(";");
            command.append(" ");
            command.append(((JsonElement) gson.fromJson(new FileReader(vMan), new TypeToken<JsonElement>() {
            }.getType())).getAsJsonObject().get("mainClass").getAsString()).append(" ");
            command.append("--assetsDir ").append(libDir.getParentFile().getPath()).append("\\assets ");
            command.append("--assetIndex ").append(vManJ.getAsJsonObject("assetIndex").get("id").getAsString()).append(" ");
            command.append("--username ").append(account.getUsername()).append(" ");
            command.append("--uuid ").append(account.getUuid()).append(" ");
            command.append("--accessToken ").append(account.getToken()).append(" ");
            command.append("--userType mojang ");
            command.append("--version ").append(vManJ.get("id").getAsString()).append(" ");
            command.append("--gameDir ").append(libDir.getAbsoluteFile().getParentFile().getPath());

//            System.out.println(command.toString());
            return command.toString();
        } else {
            System.out.println("Failed to find version manifest");
            return "ERROR";
        }
    }

    private static final OkHttpClient client = new OkHttpClient();
    private static Account login(String username, String password) throws IOException {
        Gson gson = new Gson();
        String reqUrl = "https://authserver.mojang.com/authenticate";
        JsonObject payload = new JsonObject();
        JsonObject agent = new JsonObject();
        agent.addProperty("name", "Minecraft");
        agent.addProperty("version", 1);
        payload.add("agent", agent);
        payload.addProperty("username", username);
        payload.addProperty("password", password);
        Request req = new Request.Builder().url(reqUrl).post(RequestBody.create(gson.toJson(payload), MediaType.parse("application/json; charset=utf-8"))).build();

        Response res = client.newCall(req).execute();
        JsonObject resJ = gson.fromJson(Objects.requireNonNull(res.body()).string(), new TypeToken<JsonObject>() {}.getType());
        return new Account(resJ.get("availableProfiles").getAsJsonArray().get(0).getAsJsonObject().get("name").getAsString(),
                resJ.get("selectedProfile").getAsJsonObject().get("id").getAsString(),
                resJ.get("accessToken").getAsString());
    }

    private static void startMC(String command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(
                "cmd.exe", "/c", command);
        builder.redirectErrorStream(true);
        Process p = builder.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        while (true) {
            line = r.readLine();
            if (line == null) { break; }
            System.out.println(line);
        }
    }

    static class Account {

        private final String uuid;
        private final String token;
        private final String username;

        public Account(String username, String uuid, String token) {
            this.uuid = uuid;
            this.token = token;
            this.username = username;
        }

        public String getUuid() {
            return uuid;
        }

        public String getToken() {
            return token;
        }

        public String getUsername() {
            return username;
        }
    }

}
