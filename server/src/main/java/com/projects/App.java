package com.projects;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import java.io.FileOutputStream;  
import java.io.ByteArrayInputStream;  
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.ByteArrayOutputStream;  

public class App {
    private static final String BUCKET_NAME = "comp3258a4";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Please provide an image path as a command-line argument.");
            return;
        }

        String imagePath = args[0];
        File imageFile = new File(imagePath);

        if (!imageFile.exists()) {
            System.out.println("The specified image path does not exist.");
            return;
        }

        if (!imageFile.isFile()) {
            System.out.println("The specified path does not correspond to a file.");
            return;
        }
        
        System.out.println("You have provided a valid file! Processing starting...");
        try {
            byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
            byte[] processedImageBytes = processImageBytes(imageBytes);
            
            String outputPath = System.getProperty("user.dir") + "/output/processed_image.png";
            Files.write(Paths.get(outputPath), processedImageBytes);
            
            System.out.println("Processed image saved to: " + outputPath);
        } catch (Exception e) {
            System.out.println("An error occurred while reading or writing the image file: " + e.getMessage());
        }
    }
    
    public static byte[] processImageBytes(byte[] imageBytes) {
        try {
        	String clientid = generateUniqueKey();
        	System.out.println("Hello! Your ClientId is: " + clientid);
        	System.out.println("Uploading image to s3...");
        	
            AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
            		.withRegion(Regions.AP_NORTHEAST_1)
                    .withCredentials(DefaultAWSCredentialsProviderChain.getInstance())
                    .build();

            String uniqueKey = generateUniqueKey();
            String s3Path = uploadImageToS3(s3Client, BUCKET_NAME, uniqueKey, imageBytes);

            System.out.println("Image uploaded successfully! " + s3Path);
            
        	System.out.println("Sending message to SQS...");
            AmazonSQS sqsClient = AmazonSQSClientBuilder.standard()
                    .withRegion(Regions.AP_NORTHEAST_1)
                    .withCredentials(DefaultAWSCredentialsProviderChain.getInstance())
                    .build();

            String queueUrl = "https://sqs.ap-northeast-1.amazonaws.com/390599620624/InboxQueue.fifo";
            String messageGroupId = "1";
            SendMessageRequest sendMessageRequest = new SendMessageRequest(queueUrl, uniqueKey)
            		.withMessageGroupId(messageGroupId)
                    .withMessageDeduplicationId(uniqueKey)
                    .addMessageAttributesEntry("Recipient", new MessageAttributeValue()
                            .withDataType("String")
                            .withStringValue(clientid));
            sqsClient.sendMessage(sendMessageRequest);

            System.out.println("Key added to SQS queue successfully!");

            String outboxQueueUrl = "https://sqs.ap-northeast-1.amazonaws.com/390599620624/OutBoxQueue.fifo";
            ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(outboxQueueUrl)
                    .withMaxNumberOfMessages(10)
                    .withWaitTimeSeconds(20)
                    .withMessageAttributeNames("Recipient")
                    .withVisibilityTimeout(10);

            boolean messageFound = false;
            System.out.println("Polling OutBoxQueue for messages...");

            while (!messageFound) {
                List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest).getMessages();
                
                for (Message message : messages) {
                    Map<String, MessageAttributeValue> messageAttributes = message.getMessageAttributes();
                    if (messageAttributes.containsKey("Recipient")) {
                        String recipient = messageAttributes.get("Recipient").getStringValue();

                        if (recipient.equals(clientid)) {

                            String imageKey = message.getBody();

                            byte[] resizedImage = downloadResizedImageFromS3(s3Client, BUCKET_NAME, imageKey);

                            System.out.println("Resized image downloaded successfully!");
                            
                            String receiptHandle = message.getReceiptHandle();
                            DeleteMessageRequest deleteMessageRequest = new DeleteMessageRequest(outboxQueueUrl, receiptHandle);
                            sqsClient.deleteMessage(deleteMessageRequest);
                            messageFound = true;
                            return resizedImage;
                        }
                    }
                }

                if (!messageFound) {
                    Thread.sleep(1000);
                }
            }
        } catch (Exception e) {
            System.out.println("An error occurred while processing the image: " + e.getMessage());
        }
        return null;
    }

    private static byte[] downloadResizedImageFromS3(AmazonS3 s3Client, String bucketName, String imageKey) {
        try {
            S3Object s3Object = s3Client.getObject(new GetObjectRequest(bucketName, imageKey));
            S3ObjectInputStream inputStream = s3Object.getObjectContent();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            inputStream.close();

            return outputStream.toByteArray();
        } catch (Exception e) {
            System.out.println("An error occurred while downloading the image from S3: " + e.getMessage());
            return null;
        }
    }


	private static String generateUniqueKey() {
        return UUID.randomUUID().toString();
    }

    private static String uploadImageToS3(AmazonS3 s3Client, String bucketName, String key, byte[] image) {
    	try {
            InputStream inputStream = new ByteArrayInputStream(image);

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(image.length);

            s3Client.putObject(bucketName, key, inputStream, metadata);

            return s3Client.getUrl(bucketName, key).toString();
        } catch (Exception e) {
            System.out.println("An error occurred while uploading the image to S3: " + e.getMessage());
            return null;
        }
    }
}
