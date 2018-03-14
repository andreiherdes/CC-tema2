package rest.api.common;

import java.io.File;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import rest.api.model.Person;

public class XMLGenerator {

	public static void generateXml(List<Person> list) throws ParserConfigurationException, TransformerException {
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

		// root elements
		Document doc = docBuilder.newDocument();
		Element rootElement = doc.createElement("resource");
		doc.appendChild(rootElement);
		
		for (Person person : list) {
			Element item = doc.createElement("item");
			rootElement.appendChild(item);
			
			Element id = doc.createElement("id");
			id.appendChild(doc.createTextNode(person.getId()));
			item.appendChild(id);

			Element name = doc.createElement("name");
			name.appendChild(doc.createTextNode(person.getName()));
			item.appendChild(name);

			Element occupation = doc.createElement("occupation");
			occupation.appendChild(doc.createTextNode(person.getOccupation()));
			item.appendChild(occupation);
		}
		
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		DOMSource source = new DOMSource(doc);
		StreamResult result = new StreamResult(new File(Constants.CONFIG_FILE_PATH));
		
		transformer.transform(source, result);
	}
}
