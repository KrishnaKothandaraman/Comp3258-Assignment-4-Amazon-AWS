package com.ec2projects;

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
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import java.io.FileOutputStream;  

public class App {
    private static final String BUCKET_NAME = "comp3258a4";
    
    private static String OUTBOX_QUEUE_URL = "https://sqs.ap-northeast-1.amazonaws.com/390599620624/OutBoxQueue.fifo";
    private static String INBOX_QUEUE_URL = "https://sqs.ap-northeast-1.amazonaws.com/390599620624/InboxQueue.fifo";

    public static void main(String[] args)  {
    	try {
        AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
        		.withRegion(Regions.AP_NORTHEAST_1)
                .withCredentials(DefaultAWSCredentialsProviderChain.getInstance())
                .build();
        
        AmazonSQS sqsClient = AmazonSQSClientBuilder.standard()
                .withRegion(Regions.AP_NORTHEAST_1)
                .withCredentials(DefaultAWSCredentialsProviderChain.getInstance())
                .build();
                


    	System.out.println("Worker booted up! Polling InboxQueue...");

        while (true) {
            ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(INBOX_QUEUE_URL)
                    .withMaxNumberOfMessages(10)
                    .withWaitTimeSeconds(20)
                    .withMessageAttributeNames("Recipient");
            List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest).getMessages();
            
            for (Message message : messages) {
                Map<String, MessageAttributeValue> messageAttributes = message.getMessageAttributes();
                if (messageAttributes.containsKey("Recipient")) {
                    String recipient = messageAttributes.get("Recipient").getStringValue();

                    String imageKey = message.getBody();
                    System.out.println("Received Recipient: " + recipient + " Image: " + imageKey);

                    String imagePath = downloadImageFromS3(s3Client, BUCKET_NAME, imageKey);
                    System.out.println("Downloaded image");
                    String resizedImagePath = resizeImage(imagePath, imageKey);
                    System.out.println("Saved resized image");
                    String resizedImageKey = generateUniqueKey();
                    uploadImageToS3(s3Client, BUCKET_NAME, resizedImageKey, new File(resizedImagePath));
                    System.out.println("Uploaded image to s3!");
                    sendMessageToQueue(sqsClient, resizedImageKey, OUTBOX_QUEUE_URL, recipient);
                    System.out.println("Sent message to outbox!");

                    String receiptHandle = message.getReceiptHandle();
                    deleteMessageFromQueue(sqsClient, INBOX_QUEUE_URL, receiptHandle);
                    System.out.println("Delete message");
                    deleteLocalFile(imagePath);
                    deleteLocalFile(resizedImagePath);
                    
                }
            }
            Thread.sleep(2000);

        }
        
    	} catch(Exception e) {
            System.out.println("An error occurred while processing the image: " + e.getMessage());
        }
        
    	
    }

	private static void deleteLocalFile(String imagePath) {
		File file = new File(imagePath);
	    if (file.exists()) {
	        if(!file.delete()) {
	        	System.out.println("Failed to delete: " + imagePath);
	        }
	    }
	}

	private static void deleteMessageFromQueue(AmazonSQS sqsClient, String queue_url, String receiptHandle) {
		DeleteMessageRequest deleteMessageRequest = new DeleteMessageRequest(queue_url, receiptHandle);
        sqsClient.deleteMessage(deleteMessageRequest);
	}

	private static void sendMessageToQueue(AmazonSQS sqsClient, String resizedImageKey, String queueUrl,
			String recipient) {
        String messageGroupId = "1";
        SendMessageRequest sendMessageRequest = new SendMessageRequest(queueUrl, resizedImageKey)
        		.withMessageGroupId(messageGroupId)
                .withMessageDeduplicationId(resizedImageKey)
                .addMessageAttributesEntry("Recipient", new MessageAttributeValue()
                        .withDataType("String")
                        .withStringValue(recipient));
        sqsClient.sendMessage(sendMessageRequest);			
	}

	private static void uploadImageToS3(AmazonS3 s3Client, String bucketName, String resizedImageKey, File file) {
		PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, resizedImageKey, file);
	    s3Client.putObject(putObjectRequest);
	}

	private static String generateUniqueKey() {
        return UUID.randomUUID().toString();
    }

	private static String resizeImage(String imagePath, String imageKey) {
	    String resizedImagePath = System.getProperty("user.dir") + "/resized/" + imageKey + ".png";
	    String command = "convert " + imagePath + " -resize 800x600 " + resizedImagePath;
	    try {
            File outputDir = new File(System.getProperty("user.dir") + "/resized/");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            
	        Process process = Runtime.getRuntime().exec(command);
	        int exitCode = process.waitFor();

	        if (exitCode == 0) {
	            return resizedImagePath;
	        } else {
	            System.out.println("Failed to resize the image. Exit code: " + exitCode);
	        }
	    } catch (Exception e) {
	        System.out.println("An error occurred while resizing the image: " + e.getMessage());
	    }

	    return null;
	}

	private static String downloadImageFromS3(AmazonS3 s3Client, String bucketName, String imageKey) {
		String save_path = System.getProperty("user.dir") + "/downloads/" + imageKey + ".png";
		try {
            S3Object s3Object = s3Client.getObject(new GetObjectRequest(bucketName, imageKey));
            S3ObjectInputStream inputStream = s3Object.getObjectContent();

            File outputDir = new File(System.getProperty("user.dir") + "/downloads/");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            File outputFile = new File(save_path);
            FileOutputStream outputStream = new FileOutputStream(outputFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            outputStream.close();
            inputStream.close();

            return save_path;
        } catch (Exception e) {
            System.out.println("An error occurred while downloading the image from S3: " + e.getMessage());
            return null;
        }
	}
}