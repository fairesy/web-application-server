package webserver;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hamcrest.core.SubstringMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import db.DataBase;
import model.User;
import util.HttpRequestUtils;

public class RequestHandler extends Thread {
	private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);
	private Socket connection;

	public RequestHandler(Socket connectionSocket) {
		this.connection = connectionSocket;
	}

	public void run() {
		log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(), connection.getPort());
		
		try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
			// TODO 사용자 요청에 대한 처리는 이 곳에 구현하면 된다.
			String header;
			String requestLine;
			Map<String, String> headerFields;
			String headerLines = "";
			String oneLineHeader[] = new String[2];
			int contentLength = -1;
			User user = null;
			String cookieValue = "logined=false";
			
			BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
			
			requestLine = br.readLine();
			while(!(header = br.readLine()).equals("")){
				oneLineHeader = header.split(": ");
				if(oneLineHeader[0].equals("Content-Length")) {
					contentLength = Integer.parseInt(oneLineHeader[1]);
				}
			}
			String[] tokens = requestLine.split(" ");
			String httpMethod = tokens[0];
			String url = tokens[1];
			
			if(url.startsWith("/user/create")){
				String queryString = util.IOUtils.readData(br, contentLength);
				Map<String, String> parameters = HttpRequestUtils.parseQueryString(queryString);
				user = new User(parameters.get("userId"), parameters.get("password"),
						parameters.get("name"), parameters.get("email"));
				DataBase.addUser(user);
				//완료 후 index.html로 redirect
				DataOutputStream dos = new DataOutputStream(out);
				response302Header(dos, "/index.html", cookieValue);
			}
			
			if(url.startsWith("/user/login?")) {
				DataOutputStream dos = new DataOutputStream(out);
				Map map = HttpRequestUtils.parseQueryString(url.substring(url.indexOf("?") + 1));
				user = DataBase.findUserById((String)map.get("userId"));
				if(user.getUserId().equals((String)map.get("userId"))&&user.getPassword().equals((String)map.get("password"))) {
					cookieValue="logined=true";
					response302Header(dos, "/index.html", cookieValue);
				}
				response302Header(dos, "/user/login_failed.html", cookieValue);
			}
			
			DataOutputStream dos = new DataOutputStream(out);
			byte[] body = Files.readAllBytes(new File("./webapp"+ url).toPath());
			
			String contentsType = "text/html";
			if(url.endsWith("css")){
				contentsType = "text/css";
			}
			
			response200Header(dos, body.length, contentsType);
			responseBody(dos, body);
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}

	private void response302Header(DataOutputStream dos, String resourse, String cookieValue) {
		try {
			dos.writeBytes("HTTP/1.1 302 Found \r\n");
			dos.writeBytes("Set-Cookie: " + cookieValue +"\r\n");
			dos.writeBytes("Location: http://localhost:8080" + resourse + "\r\n");
			dos.writeBytes("\r\n");
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}

	private void response200Header(DataOutputStream dos, int lengthOfBodyContent, String contentsType) {
		try {
			dos.writeBytes("HTTP/1.1 200 OK \r\n");
			dos.writeBytes("Content-Type: "+ contentsType +";charset=utf-8\r\n");
			dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
			dos.writeBytes("\r\n");
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}
	
	private void responseBody(DataOutputStream dos, byte[] body) {
		try {
			dos.write(body, 0, body.length);
			dos.writeBytes("\r\n");
			dos.flush();
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}
}
