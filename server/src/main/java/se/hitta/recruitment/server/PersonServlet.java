package se.hitta.recruitment.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.util.Models;
import org.eclipse.rdf4j.model.vocabulary.FOAF;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.UnsupportedRDFormatException;

public class PersonServlet extends HttpServlet {
	private static final long serialVersionUID = -3330161663073409678L;
	
	// Thread safe concurrent hash map
	private final Map<Integer, Person> personsMap = new ConcurrentHashMap<Integer, Person>();
	
	// Thread safe atomic integer (for ID generation)
	private final AtomicInteger idGen = new AtomicInteger();
	
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    	resp.setContentType("text/html; charset=UTF-8");
    	resp.setCharacterEncoding("UTF-8");
    	
    	final PrintWriter out = resp.getWriter();
    	
    	Integer id = null;
    	String gender = req.getParameter("gender");
    	boolean success = false;
    	
    	id = getIdFromPath(req.getPathInfo());
    	
    	if (id != null) { // User has requested to fetch a specific person ID
    		success = true;
    		
        	if (personsMap.containsKey(id))
        		out.write(personsMap.get(id).toJsonObj().toString() + "\n");
    	} else if (gender != null) {
    		success = true; // User has requested to fetch persons with a specific gender
    		
    		JsonArrayBuilder arrBuilder = Json.createArrayBuilder();
    		
    		// Utilize Java 8 stream to filter out persons with a specific gender
    		// and then map them to JsonObjBuilder objects, then finally collect them into a List
    		List<JsonObjectBuilder> persons = personsMap.values().stream()
    			.filter(s -> s.gender.equalsIgnoreCase(gender))
    			.map(s -> s.toJsonObjBuilder())
    			.collect(Collectors.toList());
    		
    		for (JsonObjectBuilder objBuilder : persons)
    			arrBuilder.add(objBuilder);
    		
    		JsonArray jsonArr = arrBuilder.build();
    		out.write(jsonArr.toString());
    	} else { // Fetch every person
    		success = true;
    		
    		JsonArrayBuilder arrBuilder = Json.createArrayBuilder();
    		
    		// Just like above, except that we now don't filter the stream for a specific gender
    		List<JsonObjectBuilder> persons = personsMap.values().stream()
        			.map(s -> s.toJsonObjBuilder())
        			.collect(Collectors.toList());
    		
    		for (JsonObjectBuilder objBuilder : persons)
    			arrBuilder.add(objBuilder);
    		
    		JsonArray jsonArr = arrBuilder.build();
    		
    		out.write(jsonArr.toString());
    	}
    	
    	if (success)
    		resp.setStatus(HttpServletResponse.SC_OK);
    	else
    		resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }
    
    private Integer getIdFromPath(String pathInfo) {
    	if (pathInfo == null)
    		return null;
    	
    	String val = pathInfo.replaceAll("[^\\d]", "");
    	
    	if (pathInfo.isEmpty())
    		return null;
    	
    	return Integer.valueOf(val);
    }
    
    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {    	
    	Model rdfModel = getInputRdfModel(req);
    	Person curPerson = null;
    	
    	Integer id = getIdFromPath(req.getPathInfo());
    	
    	if (id != null) {
            for (Resource personRes : rdfModel.filter(null, RDF.TYPE, FOAF.PERSON).subjects()) {
            	curPerson = createPerson(rdfModel, personRes);
            	curPerson.id = id;
            	personsMap.put(id, curPerson);
            }
            	        	
            resp.setStatus(HttpServletResponse.SC_OK);
    	}
    	else
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {    	
    	Model rdfModel = getInputRdfModel(req);
    	
    	Person lastPerson = null;
    	boolean personFound = false;
    	
    	// Process each person in the Model object
    	for (Resource personRes : rdfModel.filter(null, RDF.TYPE, FOAF.PERSON).subjects()) {
    		final Person person = createPerson(rdfModel, personRes);
    		personFound = false;
    		
    		// Create a stream to filter out any identical persons
    		List<Person> persons = personsMap.values().stream()
    			.filter(s -> s.equals(person))
    			.collect(Collectors.toList());
    		
    		// If the person list from the filtered stream is empty, we add this person to our map
    		if (persons.isEmpty()) {
    			person.id = idGen.incrementAndGet();
    			lastPerson = person;
    			personsMap.put(person.id, person);
    		}
    	}
    	
    	// Did we successfully create or find a person?
    	if (lastPerson != null) {
    		if (personFound)
        		resp.setStatus(HttpServletResponse.SC_ACCEPTED);
    		else
                resp.setStatus(HttpServletResponse.SC_CREATED);
    		
    		resp.addHeader("Location", "http://127.0.0.1:8080/person/" + lastPerson.id); // Append the last person created or found to the Location header
    	}
    	
    	// Invalid POST request
    	else
    		resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }
    
    private Model getInputRdfModel(HttpServletRequest req) throws RDFParseException, UnsupportedRDFormatException, IOException {
    	// Get RDF XML input stream
    	InputStream rdfXmlInput = req.getInputStream();
    	
    	// Parse RDF XML stream into a rdf4j Model object
    	return Rio.parse(rdfXmlInput, "", RDFFormat.RDFXML);
    }
    
    @SuppressWarnings("deprecation")
	private Person createPerson(Model rdfModel, Resource personRes) {
		// Get all relevant FOAF IRI:s
		Optional<Literal> gender = Models.objectLiteral(rdfModel.filter(personRes, FOAF.GENDER, null));
		Optional<Literal> given_name = Models.objectLiteral(rdfModel.filter(personRes, FOAF.GIVENNAME, null));
		Optional<Literal> family_name = Models.objectLiteral(rdfModel.filter(personRes, FOAF.FAMILYNAME, null));
		Optional<Resource> email = Models.objectResource(rdfModel.filter(personRes, FOAF.MBOX, null));
		Optional<Resource> homepage = Models.objectResource(rdfModel.filter(personRes, FOAF.HOMEPAGE, null));
		
		return new Person(-1, given_name.get().stringValue(), family_name.get().stringValue(),
				gender.get().stringValue().toLowerCase(), email.get().stringValue(), homepage.get().stringValue());
    }
}