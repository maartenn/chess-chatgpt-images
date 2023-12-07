package org.example;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;

public class Main {
    private static String API_KEY_CHATGPT;
    private static String reasonsMoveFailed = "";
    public static void main(String[] args) throws InterruptedException, IOException {
        try {
            API_KEY_CHATGPT = Files.readString(Paths.get("build/resources/main/CHATGPT_APIKEY"));
        } catch (Exception e) {
            System.err.println("API_KEY_CHATGPT file not found or invalid contents");
            System.exit(0);
        }
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(false));
            BrowserContext context = browser.newContext();
            Page page = context.newPage();
            page.navigate("https://www.chess.com/play/computer");
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Close this dialog")).click();
            page.getByLabel("Close", new Page.GetByLabelOptions().setExact(true)).click();
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Choose")).click();
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Play")).click();
            int lastNumberOfMovesBlack = -1;
            Map<String, String> coordinates = createHashmap();
            // get screenshot from id element
            for (int i = 0; i < Integer.MAX_VALUE; i++) {
                reasonsMoveFailed =""; // reset this every loop

                int numberOfMovesBlackMade = page.locator(".black.node").count();
                if (lastNumberOfMovesBlack < numberOfMovesBlackMade) {
                    System.out.println("new move from black");
                    lastNumberOfMovesBlack = numberOfMovesBlackMade;
                    String filename = "screenshot" + i + ".png";
                    page.locator("#board-play-computer").screenshot(new Locator.ScreenshotOptions().setPath(Paths.get(filename)));
                    String fromAndTo = "";
                    int retryCounter = 0;
                    String previousMove = "";
                    do {
                        retryCounter++;
                        fromAndTo = sendToChatGPTAndReturnResponseMove(filename, retryCounter, previousMove);
                        if (fromAndTo.length() != 4 ){
                            System.err.println("fromAndTo is not length 4: " + fromAndTo);
                            //TODO add someting to next request send to chatgpt that his move was wrong format
                            continue;
                        }

                        if (retryCounter > 4 ){ // if it fails 5 times then stop
                            System.err.println("move couldn't be played, out of retries, screenshot saved as endsituation.png");
                            page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get("endsituation.png")));
                            System.exit(0);
                        }
                        if(previousMove.equals("")) {
                            previousMove = new String(fromAndTo);
                        } else {
                            previousMove = String.join(" and ", previousMove, fromAndTo); //
                        }
                        boolean movePlayed = playMove(fromAndTo, page, coordinates);
                        if(movePlayed) {
                            break;
                        }
                    } while (true);

                } else {
                    System.out.println("no new moves yet");
                }

                Thread.sleep(2000);
            }
            // if hash is different then call chat gpt


        }
    }

    private static boolean playMove(String fromAndTo, Page page, Map<String, String> coordinates) {
        System.out.println("trying to play move: " + fromAndTo);
        String[] startAndEndCoordinate = parseChessMove(fromAndTo);
        // when answer returned then click on the square
        String startCoordinate = startAndEndCoordinate[0];
        String endCoordinate = startAndEndCoordinate[1];
        try {
            page.locator("." + coordinates.get(startCoordinate)).click();
        } catch (Exception e) {
            System.err.println("move couldn't be played because start field couldn't be clicked");
            if( reasonsMoveFailed.equals("")){
                reasonsMoveFailed = "start field " + startCoordinate +" couldn't be clicked";
            } else {
                reasonsMoveFailed = reasonsMoveFailed + " and start field" + startCoordinate +" also  couldn't be clicked";
            }
            return false;
        }
        // wait a bit until is highlighted?
        Locator locator = page.locator(".hint");
        try {
            locator.first().waitFor(new Locator.WaitForOptions().setTimeout(30)); // wait for hint to appear in dom (so we
        } catch (Exception e) {
            System.err.println("move couldn't be played because no hints for this piece");
            if( reasonsMoveFailed.equals("")){
                reasonsMoveFailed = "end field " + endCoordinate +" couldn't be clicked";
            } else {
                reasonsMoveFailed = reasonsMoveFailed + " and end  field " + endCoordinate + "also  couldn't be clicked";
            }
            return false;
        }
        if (locator.count() <= 0) {
            System.err.println("Hint fields can not be found, can this piece be moved? Ending game" + startCoordinate);
            if( reasonsMoveFailed.equals("")){
                reasonsMoveFailed = "end field " + endCoordinate +" couldn't be clicked";
            } else {
                reasonsMoveFailed = reasonsMoveFailed + " and end  field" + endCoordinate + "also  couldn't be clicked";
            }
            return false;
        } else {
            if (locator.all().stream().anyMatch(e -> e.getAttribute("class").contains(coordinates.get(endCoordinate)))) {
                System.out.println("move can be played");
                System.out.println("click destination field: " + endCoordinate + " " + coordinates.get(endCoordinate));
                System.out.println("end field is visible: " + page.locator("." + coordinates.get(endCoordinate)).isVisible());
                page.locator("." + coordinates.get(endCoordinate)).click(new Locator.ClickOptions().setForce(true));
            } else {
                if( reasonsMoveFailed.equals("")){
                    reasonsMoveFailed = "end field" + endCoordinate +" couldn't be clicked";
                } else {
                    reasonsMoveFailed = reasonsMoveFailed + " and end  field" + endCoordinate + "also  couldn't be clicked";
                }
                return false;
            }
        }
        return true;
    }

    public static String sendToChatGPTAndReturnResponseMove(String image_path, int retryCounter, String previousMove) {
        if(retryCounter > 0) {
            System.out.println("retrying to ask chatgpt because move cannot be made: " + retryCounter + " previous move: "+ previousMove);
        }
        System.out.println("start asking chatgpt: " + image_path);
        // OpenAI API Key
        // Getting the base64 string
        String base64_image = encodeImage(image_path);

        // API endpoint and request payload
        String endpoint = "https://api.openai.com/v1/chat/completions";
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", "gpt-4-vision-preview");

        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        String question = retryCounter == 0 ? "What’s the best next move for white? Can you give me the answer without any context, just the expanded algebraic notation without piece names? example: e2e4" :
            "Please retry, the previous move(s) " + previousMove + " you gave was/were invalid! Reasons are: " + reasonsMoveFailed +" What’s the best next move for white? Can you give me the answer without any context, just the expanded algebraic notation without piece names? example: e2e4";
        System.out.println("Question send to chatgpt: " + question);
        Content[] content = new Content[] {
            new TextContent(question),
            new ImageUrlContent("data:image/jpeg;base64," + base64_image)
        };

        message.put("content", content);

        payload.put("messages", new Object[] {message});
        payload.put("max_tokens", 300);

        // Prepare and send the HTTP request
        try {
            HttpClient client = HttpClient.newHttpClient();

            String json = new Gson().toJson(payload);
//            System.out.println("Payload to be sent; " + json);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(endpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY_CHATGPT)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String contentFromResponse = getContentFromResponse(response.body());
            return contentFromResponse;
        } catch (IOException | InterruptedException | java.net.URISyntaxException e) {
            e.printStackTrace();
        }
        return "";
    }

    private static String encodeImage(String imagePath) {
        try {
            Path path = Paths.get(imagePath);
            byte[] imageBytes = Files.readAllBytes(path);
            return Base64.getEncoder().encodeToString(imageBytes);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static Map<String, String> createHashmap() {
        // Create a HashMap to store the mapping
        Map<String, String> chessCoordinateMap = new HashMap<>();

        // Populate the HashMap with the mapping of chess coordinates
        chessCoordinateMap.put("A1", "square-11");
        chessCoordinateMap.put("A2", "square-12");
        chessCoordinateMap.put("A3", "square-13");
        chessCoordinateMap.put("A4", "square-14");
        chessCoordinateMap.put("A5", "square-15");
        chessCoordinateMap.put("A6", "square-16");
        chessCoordinateMap.put("A7", "square-17");
        chessCoordinateMap.put("A8", "square-18");

        chessCoordinateMap.put("B1", "square-21");
        chessCoordinateMap.put("B2", "square-22");
        chessCoordinateMap.put("B3", "square-23");
        chessCoordinateMap.put("B4", "square-24");
        chessCoordinateMap.put("B5", "square-25");
        chessCoordinateMap.put("B6", "square-26");
        chessCoordinateMap.put("B7", "square-27");
        chessCoordinateMap.put("B8", "square-28");

        chessCoordinateMap.put("C1", "square-31");
        chessCoordinateMap.put("C2", "square-32");
        chessCoordinateMap.put("C3", "square-33");
        chessCoordinateMap.put("C4", "square-34");
        chessCoordinateMap.put("C5", "square-35");
        chessCoordinateMap.put("C6", "square-36");
        chessCoordinateMap.put("C7", "square-37");
        chessCoordinateMap.put("C8", "square-38");

        chessCoordinateMap.put("D1", "square-41");
        chessCoordinateMap.put("D2", "square-42");
        chessCoordinateMap.put("D3", "square-43");
        chessCoordinateMap.put("D4", "square-44");
        chessCoordinateMap.put("D5", "square-45");
        chessCoordinateMap.put("D6", "square-46");
        chessCoordinateMap.put("D7", "square-47");
        chessCoordinateMap.put("D8", "square-48");

        chessCoordinateMap.put("E1", "square-51");
        chessCoordinateMap.put("E2", "square-52");
        chessCoordinateMap.put("E3", "square-53");
        chessCoordinateMap.put("E4", "square-54");
        chessCoordinateMap.put("E5", "square-55");
        chessCoordinateMap.put("E6", "square-56");
        chessCoordinateMap.put("E7", "square-57");
        chessCoordinateMap.put("E8", "square-58");

        chessCoordinateMap.put("F1", "square-61");
        chessCoordinateMap.put("F2", "square-62");
        chessCoordinateMap.put("F3", "square-63");
        chessCoordinateMap.put("F4", "square-64");
        chessCoordinateMap.put("F5", "square-65");
        chessCoordinateMap.put("F6", "square-66");
        chessCoordinateMap.put("F7", "square-67");
        chessCoordinateMap.put("F8", "square-68");

        chessCoordinateMap.put("G1", "square-71");
        chessCoordinateMap.put("G2", "square-72");
        chessCoordinateMap.put("G3", "square-73");
        chessCoordinateMap.put("G4", "square-74");
        chessCoordinateMap.put("G5", "square-75");
        chessCoordinateMap.put("G6", "square-76");
        chessCoordinateMap.put("G7", "square-77");
        chessCoordinateMap.put("G8", "square-78");

        chessCoordinateMap.put("H1", "square-81");
        chessCoordinateMap.put("H2", "square-82");
        chessCoordinateMap.put("H3", "square-83");
        chessCoordinateMap.put("H4", "square-84");
        chessCoordinateMap.put("H5", "square-85");
        chessCoordinateMap.put("H6", "square-86");
        chessCoordinateMap.put("H7", "square-87");
        chessCoordinateMap.put("H8", "square-88");
        return chessCoordinateMap;
    }

    public static String getContentFromResponse(String jsonResponse) {
        JsonParser jsonParser = new JsonParser();
        JsonObject jsonObject = jsonParser.parse(jsonResponse).getAsJsonObject();

        // Get the "content" field from the first choice (assuming there's only one choice)
        JsonArray choicesArray = jsonObject.getAsJsonArray("choices");
        if (choicesArray.size() > 0) {
            JsonObject firstChoice = choicesArray.get(0).getAsJsonObject();
            JsonObject message = firstChoice.getAsJsonObject("message");
            String content = message.get("content").getAsString();
            content = parseMessageToFullAlgebraic(content);
            // Print the "content" field value
            System.out.println("Content: " + content);
            return content;
        }
        System.out.println("No choices found in the JSON response.");
        return "";
    }

    /**
     * Handles castling and removes the x for capture
     * @param content move returned by chat gpt
     * @return a full algebraic notation, for castling its the start and end position of (white) king
     */
    private static String parseMessageToFullAlgebraic(String content) {
        content = content.replaceAll("x",""); // replace x when it's a capture
// handle castling
        if (content.equals("O-O-O")) {
            content = "e1c1";
        } else if (content.equals("O-O")) {
            content = "e1g1";
        }
        return content;
    }

    public static String[] parseChessMove(String algebraicNotation) {
        if (algebraicNotation.length() == 4) {
            String from = algebraicNotation.substring(0, 2).toUpperCase();
            String to = algebraicNotation.substring(2, 4).toUpperCase();
            return new String[] {from, to};
        }
        return null; // Invalid algebraic notation
    }
}
