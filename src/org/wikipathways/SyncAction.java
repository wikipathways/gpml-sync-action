package org.wikipathways;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
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

/**
 * Class that pulls in changes from the classic WikiPahtways database
 * There are two options how to run this script:
 * 		1) provide a date in the format yyyyMMddhhmmss (pull in changes since then)
 * 		2) provide a file with a list of WP identifiers and a sync will be performed for those pathways on demand
 * 
 * @author mkutmon
 *
 */
public class SyncAction {

	// args = date of last sync
	public static void main(String[] args) throws ParseException, IOException, ConverterException, Exception {
		WikiPathwaysClient client = new WikiPathwaysClient(new URL("https://webservice.wikipathways.org"));
		List<String> pathways2Sync = new ArrayList<String>();
		Map<String, String> pathwaysNewRev = new HashMap<String, String>();

		// argument is date of last sync
		if(args.length==1 && args[0].matches("[0-9]+")) {
			System.out.println("Getting pathways changed / added since " + args[0]);
			
			// get all recent pathway edits
			WSPathwayInfo [] changedPwy = client.getRecentChanges(new SimpleDateFormat("yyyyMMddhhmmss").parse(args[0]));
			Set<String> changed = new HashSet<String>();
			Map<String, String> changedRev = new HashMap<String, String>();
	
			for(WSPathwayInfo i : changedPwy) {
				changed.add(i.getId());
				changedRev.put(i.getId(), i.getRevision());
			}
				
			System.out.println("All changed pathways on classic site: " + changed.size() + "\t" + changed); 
	
			List<String> wpidList = getPathwaysLive();
			System.out.println("Current pathways on live site: " + wpidList.size() ); 
	
			WSCurationTag [] curatedPwy = client.getCurationTagsByName("Curation:AnalysisCollection");
			Set<String> curated = new HashSet<String>();
			int newApprovedPathways = 0;
			for(WSCurationTag i : curatedPwy) {
				curated.add(i.getPathway().getId());
				//also collect newly approved, regardless of recent edit
				if(!wpidList.contains(i.getPathway().getId())) {
					changed.add(i.getPathway().getId());
					changedRev.put(i.getPathway().getId(), i.getPathway().getRevision());
					newApprovedPathways++;
				}
			}
			
			System.out.println("Approved pathways on classic site: " + curatedPwy.length + "\t" + curated.size()); 
	
			// TODO: check if there are changed pathways that are not approved on the classic site but they are on the live site
			// shouldn't happen but with the on demand sync it is possible
			// TODO: check if any pathways have been deleted
			
			curated.retainAll(changed);
			pathways2Sync.addAll(curated);
			for(String id : changedRev.keySet()) {
				if(pathways2Sync.contains(id)) {
					pathwaysNewRev.put(id, changedRev.get(id));
				}
			}
			
			System.out.println("Curated pathways that have been changed or added: " + pathways2Sync.size() + " (" + newApprovedPathways + " new pathways)");
		} else if(new File(args[0]).exists()) {
			System.out.println("Getting pathways from file: " + args[0]);
			pathways2Sync = Files.readAllLines(Paths.get(args[0]));
			for(String id: pathways2Sync) {
				WSPathwayInfo i = client.getPathwayInfo(id);
				pathwaysNewRev.put(id, i.getRevision());
			}
			System.out.println(pathways2Sync.size() + " pathways need to be synced.");

		} else {
			System.out.println("Invalid argument!");
		}
		
		// sync all recently changed or selected pathways
		Map<String, Exception> error = new HashMap<String, Exception>();
		
		if (pathways2Sync.size() > 0) {
			System.out.println("Syncing of " + pathways2Sync.size() + " pathways started.");
			for (String id : pathways2Sync) {
				try {
					URL urlPathway = new URL("https://www.wikipathways.org/wpi/wpi.php?action=downloadFile&type=gpml&pwTitle=Pathway:" + id);
					Pathway model = new Pathway();
					model.readFromXml(urlPathway.openStream(), false);
					model.getMappInfo().setMapInfoDataSource("WikiPathways");
					model.getMappInfo().setCopyright("");
					Set<Comment> del = new HashSet<Comment>();
					for (Comment c : model.getMappInfo().getComments()) {
						if (c.getSource() != null && (c.getSource().equals("WikiPathways-category")
								|| c.getSource().equals("GenMAPP notes") || c.getSource().equals("GenMAPP remarks"))) {
							del.add(c);
						}
					}
					model.getMappInfo().getComments().removeAll(del);
					model.getMappInfo().setEmail("");
					model.getMappInfo().setMaintainer("");
					model.getMappInfo().setVersion(id + "_r" + pathwaysNewRev.get(id));
					model = getAuthors(model, id);
	
					File dir = new File("pathways/" + id);
					dir.mkdirs();
					model.writeToXml(new File(dir, id + ".gpml"), false);
				} catch(Exception e) {
					error.put(id, e);
				}
			}
			System.out.println("Syncing of " + pathways2Sync.size() + " pathways done.\t" + pathways2Sync);
			System.out.println("Errors occured in: " + error.size() + " pathways\n");
			for(String id : error.keySet()) {
				System.out.println(id +"\n");
				error.get(id).printStackTrace();
				System.out.println("");
			}
			if(error.size() > 0) {
				throw new Exception("Synchronization failed.");
			}
		}
	}
	
	private static List<String> getPathwaysLive() {
		File dirPath = new File("pathways/");
		List<String> wpidList = new ArrayList<>();
    	File[] dirList = dirPath.listFiles(File::isDirectory);
		if (dirList != null) {
			for (File d : dirList) {
			  if (d.isDirectory()) {
				wpidList.add(d.getName());
			  }
			}
		  }
		return wpidList;
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
