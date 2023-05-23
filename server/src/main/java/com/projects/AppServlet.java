package com.projects;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Base64;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;

import org.json.JSONException;
import org.json.JSONObject;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.projects.App;

public class AppServlet extends HttpServlet {

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
	  BufferedReader reader = request.getReader();
	  JsonObject jsonRequest = new Gson().fromJson(reader, JsonObject.class);
	    reader.close();

		if (!jsonRequest.has("image")) {
    	    JSONObject responseJson = new JSONObject();
    	    responseJson.put("type", "fail");
    	    responseJson.put("message", "image field is missing!");

    	    response.setContentType("application/json");

    	    PrintWriter writer = response.getWriter();
    	    writer.print(responseJson.toString());
    	    writer.close();
		    return;
		}
	    
      byte[] imageBytes;
      
      
      try {
          imageBytes = Base64.getDecoder().decode(jsonRequest.get("image").getAsString());
      } catch (IllegalArgumentException e) {
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          response.getWriter().println("Invalid image bytes.");
          return;
      }
      
      try {
    	    BufferedImage originalImage = loadImageFromBytes(imageBytes);
    	    
    	    if (originalImage == null) {
    	        // Image loading failed
    	        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    	        JsonObject jsonResponse = new JsonObject();
    	        jsonResponse.addProperty("type", "Fail");
    	        jsonResponse.addProperty("message", "Incorrect image bytes provided");
    	        response.setContentType("application/json");
    	        response.getWriter().println(jsonResponse.toString());
    	        return;
    	    }
    	    BufferedImage transformedImage = performImageTransformations(originalImage);

    	    byte[] transformedImageBytes = convertImageToBytes(transformedImage);

    	    JSONObject responseJson = new JSONObject();
    	    responseJson.put("type", "success");
    	    responseJson.put("data", Base64.getEncoder().encodeToString(transformedImageBytes));

    	    response.setContentType("application/json");

    	    PrintWriter writer = response.getWriter();
    	    writer.print(responseJson.toString());
    	    writer.close();
    	} catch (IOException e) {

    	    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    	    response.setContentType("application/json");

    	    JSONObject errorJson = new JSONObject();
    	    errorJson.put("type", "error");
    	    errorJson.put("message", "An error occurred.");

    	    PrintWriter writer = response.getWriter();
    	    writer.print(errorJson.toString());
    	    writer.close();
    	}
  }

  private BufferedImage loadImageFromBytes(byte[] imageBytes) throws IOException {
	    try {
	        ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes);
	        return ImageIO.read(inputStream);
	    } catch (IOException e) {
	        System.out.println("An error occurred while loading the image: " + e.getMessage());
	        return null;
	    }
  }

  private BufferedImage performImageTransformations(BufferedImage originalImage) {
	    try {
	        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
	        ImageIO.write(originalImage, "png", outputStream);

	        byte[] imageBytes = outputStream.toByteArray();
	        byte[] processedImageBytes = App.processImageBytes(imageBytes);

	        ByteArrayInputStream inputStream = new ByteArrayInputStream(processedImageBytes);
	        return ImageIO.read(inputStream);
	    } catch (IOException e) {
	        System.out.println("An error occurred during image transformations: " + e.getMessage());
	        return null;
	    }
	}

  private byte[] convertImageToBytes(BufferedImage image) throws IOException {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      ImageIO.write(image, "png", outputStream);
      return outputStream.toByteArray();
  }

}