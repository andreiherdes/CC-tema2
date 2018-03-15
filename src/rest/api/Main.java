package rest.api;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import rest.api.common.Constants;
import rest.api.common.XMLGenerator;
import rest.api.model.Person;

public class Main {

	private static final String PORT = "8080";
	private static final String RESOURCE_NAME = "myResource";
	private static List<Person> resources = new ArrayList<>();

	public static void main(String[] args) throws IOException, ParserConfigurationException, TransformerException {
		try {
			initServer();
		} catch (SAXException | ParserConfigurationException e) {
			e.printStackTrace();
		}

		String mainUri = "/" + RESOURCE_NAME + "/";
		String pattern = mainUri + "[^/]*";
		Pattern regex = Pattern.compile(pattern);

		String encoding = "UTF-8";

		HttpServer server = HttpServer.create(new InetSocketAddress(Integer.parseInt(PORT)), 0);
		server.createContext("/", new HttpHandler() {
			@Override
			public void handle(final HttpExchange exchange) throws IOException {

				String requestedUri = exchange.getRequestURI().toString();
				Matcher matcher = regex.matcher(requestedUri);
				StringBuilder response = new StringBuilder();
				OutputStream os = exchange.getResponseBody();

				exchange.getResponseHeaders().set("Content-Type", "text/xml; charset=" + encoding);

				if (!matcher.find()) {
					response.append("<response>\n");
					response.append("\t" + Constants.ERROR + "\n");
					response.append("<response>\n");
					exchange.sendResponseHeaders(404, response.toString().getBytes().length);

					os.write(response.toString().getBytes());
					os.close();
				} else {
					if (mainUri.equals(requestedUri)) {

						switch (exchange.getRequestMethod()) {
						case "GET":

							if (resources != null && !resources.isEmpty()) {
								response.append("<response>");
								for (Person person : resources) {
									response.append(person.toString());
								}
								response.append("</response>");
								exchange.sendResponseHeaders(200, response.toString().getBytes().length);
							} else {
								response.append("<response>");
								response.append(Constants.ERROR);
								response.append("</response>");
								exchange.sendResponseHeaders(404, response.toString().getBytes().length);
							}

							os.write(response.toString().getBytes());
							os.close();

							break;
						case "PUT":

							InputStream xmlPut = exchange.getRequestBody();
							List<Person> persons = new ArrayList<>();
							response.append("<response>");
							try {
								parseXml(xmlPut, persons);
								resources = persons;

								response.append("Resource updated");
								response.append("</response>");
								exchange.sendResponseHeaders(200, response.toString().getBytes().length);

							} catch (XPathExpressionException | SAXException | ParserConfigurationException e) {
								response.append(Constants.ERROR + "\n");
								response.append(e);
								response.append("</response>");
								exchange.sendResponseHeaders(400, response.toString().getBytes().length);
								e.printStackTrace();

							} finally {
								os.write(response.toString().getBytes());
								os.close();
							}
							break;
						case "POST":
							InputStream xml = exchange.getRequestBody();
							List<Person> person = new ArrayList<>();

							try {
								parseXml(xml, person);
								resources.add(person.get(0));
								response.append("<response>\n");
								response.append("\tResource updated\n");
								response.append("</response>");
								exchange.sendResponseHeaders(200, response.toString().getBytes().length);

							} catch (XPathExpressionException | SAXException | ParserConfigurationException e) {
								response.append("<response>\n");
								response.append(Constants.BAD_REQUEST + "\n");
								response.append(e);
								response.append("</response>");
								exchange.sendResponseHeaders(400, response.toString().getBytes().length);
								e.printStackTrace();

							} finally {
								os.write(response.toString().getBytes());
								os.close();
							}

							break;
						case "DELETE":

							response.append("<response>\n");
							if (resources == null || resources.isEmpty()) {
								response.append("Resource already deleted\n");
							} else {
								response.append("Resource deleted successfully\n");
								resources = null;
							}

							response.append("</response>");

							exchange.sendResponseHeaders(200, response.toString().getBytes().length);
							os.write(response.toString().getBytes());
							os.close();
							break;
						default:

							response.append("<response>");
							response.append("405 Method not allowed");
							response.append("</response>");
							exchange.sendResponseHeaders(405, response.toString().getBytes().length);
							os.write(response.toString().getBytes());
							os.close();
							break;
						}
					} else {
						String id = requestedUri.substring(requestedUri.lastIndexOf('/') + 1);
						Person person = getPersonById(id);
						switch (exchange.getRequestMethod()) {
						case "GET":

							if (person != null) {
								response.append("<response>");
								response.append("Id found.\n");
								response.append(person.toString());
								response.append("</response>");
								exchange.sendResponseHeaders(200, response.toString().getBytes().length);
							} else {
								response.append("<response>");
								response.append(Constants.ERROR);
								response.append("</response>");
								exchange.sendResponseHeaders(404, response.toString().getBytes().length);
							}

							os.write(response.toString().getBytes());
							os.close();

							break;
						case "PUT":
							InputStream xmlPut = exchange.getRequestBody();

							try {
								Person newPerson = getNewPerson(xmlPut);
								response.append("<response>");
								if (person == null) {
									resources.add(newPerson);
									response.append("Id not found, added a new item\n");
								} else {
									resources.remove(person);
									resources.add(newPerson);
									response.append("Id found, updated the existing item\n");
								}
								response.append(newPerson.toString());
								response.append("</response>");
								exchange.sendResponseHeaders(200, response.toString().getBytes().length);
							} catch (XPathExpressionException | ParserConfigurationException | SAXException e) {
								response.append(Constants.BAD_REQUEST + "\n");
								response.append(e);
								e.printStackTrace();
								exchange.sendResponseHeaders(400, response.toString().getBytes().length);
							} finally {
								os.write(response.toString().getBytes());
								os.close();
							}
							break;
						case "POST":
							InputStream xmlPatch = exchange.getRequestBody();

							response.append("<response>");

							try {
								if (person == null) {
									response.append("Id not found\n");
									response.append("</response>");
									exchange.sendResponseHeaders(404, response.toString().getBytes().length);
								} else {
									Person newPerson = getNewPerson(xmlPatch);
									resources.remove(person);
									resources.add(newPerson);
									response.append("Id found, updated the existing item\n");
									response.append(newPerson);
									response.append("</response>");
									exchange.sendResponseHeaders(200, response.toString().getBytes().length);
								}

							} catch (XPathExpressionException | ParserConfigurationException | SAXException e) {
								response.append(Constants.BAD_REQUEST + "\n");
								response.append(e);
								response.append("</response>");
								e.printStackTrace();
								exchange.sendResponseHeaders(400, response.toString().getBytes().length);
							} finally {
								os.write(response.toString().getBytes());
								os.close();
							}
							break;
						case "DELETE":
							response.append("<response>\n");
							if (person == null) {
								response.append("\tId not found\n");
								response.append("</response>\n");
								exchange.sendResponseHeaders(404, response.toString().getBytes().length);
							} else {
								resources.remove(person);
								response.append("\tId found, deleted the record\n");
								response.append("</response>\n");
								exchange.sendResponseHeaders(200, response.toString().getBytes().length);
							}

							os.write(response.toString().getBytes());
							os.close();
							break;
						default:
							response.append("<response>\n");
							response.append("405 Method not allowed");
							response.append("</response>\n");
							exchange.sendResponseHeaders(405, response.toString().getBytes().length);
							os.write(response.toString().getBytes());
							os.close();
							break;
						}

					}
				}
				try {
					XMLGenerator.generateXml(resources);
				} catch (ParserConfigurationException | TransformerException e) {
					e.printStackTrace();
				}
			}

		});

		server.setExecutor(null);
		server.start();

	}

	protected static Person getNewPerson(InputStream xml)
			throws ParserConfigurationException, SAXException, IOException, XPathExpressionException {
		DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = null;

		builder = builderFactory.newDocumentBuilder();
		XPath xPath = XPathFactory.newInstance().newXPath();
		Document xmlDocument = builder.parse(xml);

		Node node = (Node) xPath.compile("item").evaluate(xmlDocument, XPathConstants.NODE);

		Person person = new Person();

		if (node.getNodeType() == Node.ELEMENT_NODE) {
			Element itemElement = (Element) node;

			person.setId(itemElement.getElementsByTagName("id").item(0).getTextContent());
			person.setName(itemElement.getElementsByTagName("name").item(0).getTextContent());
			person.setOccupation(itemElement.getElementsByTagName("occupation").item(0).getTextContent());
		}

		return person;
	}

	protected static Person getPersonById(String id) {
		Optional<Person> matchingPerson = resources.stream().filter(p -> p.getId().equals(id)).findFirst();
		Person person = matchingPerson.orElse(null);

		return person;
	}

	protected static void parseXml(InputStream xml, List<Person> persons)
			throws SAXException, IOException, XPathExpressionException, ParserConfigurationException {
		DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = null;

		builder = builderFactory.newDocumentBuilder();
		XPath xPath = XPathFactory.newInstance().newXPath();
		Document xmlDocument = builder.parse(xml);
		Node node = (Node) xPath.compile("items").evaluate(xmlDocument, XPathConstants.NODE);
		if (node.getNodeType() == Node.ELEMENT_NODE) {
			NodeList items = node.getChildNodes();
			for (int i = 0; i < items.getLength(); i++) {
				Node item = items.item(i);
				if (item.getNodeType() == Node.ELEMENT_NODE) {
					Element itemElement = (Element) item;
					Person person = new Person();
					person.setId(itemElement.getElementsByTagName("id").item(0).getTextContent());
					person.setName(itemElement.getElementsByTagName("name").item(0).getTextContent());
					person.setOccupation(itemElement.getElementsByTagName("occupation").item(0).getTextContent());

					persons.add(person);
				}
			}

		}

	}

	public static void initServer() throws SAXException, IOException, ParserConfigurationException {
		File xmlConfigFile = new File(Constants.CONFIG_FILE_PATH);
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.parse(xmlConfigFile);

		doc.getDocumentElement().normalize();

		NodeList resource = (NodeList) doc.getElementsByTagName(Constants.RESOURCE_ELEMENT).item(0);

		for (int i = 0; i < resource.getLength(); i++) {
			Node item = resource.item(i);
			if (item.getNodeType() == Node.ELEMENT_NODE) {
				Element itemElement = (Element) item;
				Person person = new Person();
				person.setId(itemElement.getElementsByTagName("id").item(0).getTextContent());
				person.setName(itemElement.getElementsByTagName("name").item(0).getTextContent());
				person.setOccupation(itemElement.getElementsByTagName("occupation").item(0).getTextContent());

				resources.add(person);
			}
		}
	}
}
