package org.example;
import com.google.gson.annotations.SerializedName;

class ImageUrl {
    @SerializedName("url")
    private String url;

    public ImageUrl(String url) {
        this.url = url;
    }
}

class Content {
    @SerializedName("type")
    private String type;

    public Content(String type) {
        this.type = type;
    }
}

class TextContent extends Content {
    @SerializedName("text")
    private String text;

    public TextContent(String text) {
        super("text");
        this.text = text;
    }
}

class ImageUrlContent extends Content {
    @SerializedName("image_url")
    private ImageUrl imageUrl;

    public ImageUrlContent(String imageUrl) {
        super("image_url");
        this.imageUrl = new ImageUrl(imageUrl);
    }
}

class Message {
    @SerializedName("content")
    private Content[] content;

    public Message(Content[] content) {
        this.content = content;
    }
}
