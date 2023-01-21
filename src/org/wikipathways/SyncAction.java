package org.wikipathways;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.pathvisio.core.model.ConverterException;
import org.pathvisio.core.model.Pathway;
import org.pathvisio.core.model.PathwayElement.Comment;
import org.pathvisio.wikipathways.webservice.WSCurationTag;
import org.pathvisio.wikipathways.webservice.WSPathwayInfo;
import org.wikipathways.client.WikiPathwaysClient;

public class SyncAction {

	// args = date of last sync
	public static void main(String[] args) throws ParseException, IOException, ConverterException {
		WikiPathwaysClient client = new WikiPathwaysClient(new URL("https://webservice.wikipathways.org"));
		WSPathwayInfo [] changedPwy = client.getRecentChanges(new SimpleDateFormat("yyyyMMddhhmmss").parse(args[0]));
		Set<String> changed = new HashSet<String>();
		Map<String, String> changedRev = new HashMap<String, String>();

		for(WSPathwayInfo i : changedPwy) {
			changed.add(i.getId());
			changedRev.put(i.getId(), i.getRevision());

		}
		System.out.println(changedPwy.length + "\t" + changed.size() + "\t" + changed); 

		WSCurationTag [] curatedPwy = client.getCurationTagsByName("Curation:AnalysisCollection");
		Set<String> curated = new HashSet<String>();
//		Map<String, String> taggedRev = new HashMap<String, String>();
		for(WSCurationTag i : curatedPwy) {
			curated.add(i.getPathway().getId());
//			taggedRev.put(i.getPathway().getId(), i.getPathway().getRevision());
		}
		
		System.out.println(curatedPwy.length + "\t" + curated.size()); 

		curated.retainAll(changed);
		
		System.out.println(curated.size());

		for(String id : curated) {
//			URL urlPathway = new URL("https://www.wikipathways.org//wpi/wpi.php?action=downloadFile&type=gpml&pwTitle=Pathway:" + id + "&oldid=" + taggedRev.get(id));
			URL urlPathway = new URL("https://www.wikipathways.org//wpi/wpi.php?action=downloadFile&type=gpml&pwTitle=Pathway:" + id);
			URLConnection urlConPathway = urlPathway.openConnection();
			//add user agent for Ubuntu/GH Action operation
			urlConPathway.addRequestProperty("User-Agent", "Mozilla");
			Pathway model = new Pathway();
			model.readFromXml(urlConPathway.getInputStream(), false);
			model.getMappInfo().setMapInfoDataSource("WikiPathways");
			model.getMappInfo().setCopyright("");
			Set<Comment> del = new HashSet<Comment>();
			for(Comment c : model.getMappInfo().getComments()) {
				if(c.getSource()!= null && (c.getSource().equals("WikiPathways-category") || c.getSource().equals("GenMAPP notes") || c.getSource().equals("GenMAPP remarks"))) {
					del.add(c);
				}
			}
			model.getMappInfo().getComments().removeAll(del);
			model.getMappInfo().setEmail("");
			model.getMappInfo().setMaintainer("");
			model.getMappInfo().setVersion(id + "_r" + changedRev.get(id));
			model = getAuthors(model, id);
			
			File dir = new File("pathways/" + id);
			dir.mkdirs();
			model.writeToXml(new File(dir, id + ".gpml"), false);
		}
	}
	
	private static Pathway getAuthors(Pathway model, String wpId) throws IOException {
		URL url = new URL("https://webservice.wikipathways.org/getPathwayHistory?pwId=" + wpId + "&timestamp=20000101&format=xml");
		URLConnection urlCon = url.openConnection();
		BufferedReader reader = new BufferedReader(new InputStreamReader(urlCon.getInputStream()));
		String line;
		List<String> authors = new ArrayList<String>();
		String lastEdited = "";
		while((line = reader.readLine()) != null) {
			if(line.contains("ns2:user")) {
				String auth = line.replace("<ns2:user>", "");
				auth = auth.replace("</ns2:user>", "");
				auth = auth.replace("\t", "");
				if(!authors.contains(auth)) authors.add(auth);
			} else if(line.contains("<ns2:timestamp>")) {
				String time = line.replace("<ns2:timestamp>", "");
				time = time.replace("</ns2:timestamp>", "");
				time = time.replace("\t", "");
				lastEdited = time;
			}
		}
		reader.close();
		model.getMappInfo().setAuthor(authors.toString());
		model.getMappInfo().setLastModified(lastEdited);
		return model;
	}
}
