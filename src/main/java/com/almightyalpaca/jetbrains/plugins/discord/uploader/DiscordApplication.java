package com.almightyalpaca.jetbrains.plugins.discord.uploader;

import com.google.gson.*;
import okhttp3.*;
import org.apache.commons.collections4.MultiMapUtils;
import org.apache.commons.collections4.SetValuedMap;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;
import java.util.stream.StreamSupport;

public class DiscordApplication
{
    @NotNull
    private static final Gson GSON = new GsonBuilder().create();
    @NotNull
    private static final MediaType MEDIA_TYPE_JSON = Objects.requireNonNull(MediaType.parse("application/json; charset=utf-8"));
    @NotNull
    private final OkHttpClient client;
    @NotNull
    private final String id;
    @NotNull
    private final String token;

    public DiscordApplication(@NotNull OkHttpClient client, @NotNull String id, @NotNull String token)
    {
        this.client = Objects.requireNonNull(client, "client");
        this.id = Objects.requireNonNull(id, "id");
        this.token = Objects.requireNonNull(token, "token");
    }

    @NotNull
    public SetValuedMap<String, String> getUploadedAssets() throws IOException, NullPointerException
    {
        final JsonParser parser = new JsonParser();

        Request getRequest = new Request.Builder()
                .url("https://discordapp.com/api/oauth2/applications/" + this.id + "/assets")
                .get()
                .header("user-agent", "Mozilla/5.0 (Windows NT 6.2; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3472.3 Safari/537.36")
                .build();

        Response response = this.client.newCall(getRequest).execute();

        JsonArray assets = parser.parse(Objects.requireNonNull(response.body()).charStream()).getAsJsonArray();
        response.close();

        SetValuedMap<String, String> map = MultiMapUtils.newSetValuedHashMap();

        StreamSupport.stream(assets.spliterator(), false)
                .map(JsonElement::getAsJsonObject)
                .forEach(jsonObject -> map.put(jsonObject.get("name").getAsString(), jsonObject.get("id").getAsString()));

        return map;
    }

    public void uploadAsset(@NotNull Path file, @NotNull String name) throws IOException
    {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(name, "name");

        JsonObject object = new JsonObject();
        object.addProperty("image", "data:image/png;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(file)));
        object.addProperty("name", name);
        object.addProperty("type", 1);

        String json = GSON.toJson(object);

        RequestBody body = RequestBody.create(MEDIA_TYPE_JSON, json);

        Request request = new Request.Builder()
                .url("https://discordapp.com/api/oauth2/applications/" + this.id + "/assets")
                .post(body)
                .header("authorization", this.token)
                .header("user-agent", "Mozilla/5.0 (Windows NT 6.2; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3472.3 Safari/537.36")
                .build();

        this.client.newCall(request).execute().close();
    }

    public void deleteAsset(@NotNull String assetId) throws IOException
    {
        Objects.requireNonNull(assetId, "assetId");

        Request deleteRequest = new Request.Builder()
                .url("https://discordapp.com/api/oauth2/applications/" + this.id + "/assets/" + assetId)
                .delete()
                .header("authorization", token)
                .header("user-agent", "Mozilla/5.0 (Windows NT 6.2; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3472.3 Safari/537.36")
                .build();

        this.client.newCall(deleteRequest).execute().close();
    }
}
