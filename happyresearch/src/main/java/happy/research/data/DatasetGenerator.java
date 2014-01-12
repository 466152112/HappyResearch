package happy.research.data;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;

/**
 * Generate data set from RDF files
 * 
 * @author guoguibing
 * 
 */
public class DatasetGenerator
{

	public static void main(String[] args) throws Exception
	{
		String dirPath = "C:/Users/guoguibing/Desktop/userProfiles";
		DatasetGenerator.generateDataset(dirPath); // ratings and trust
	}


	/**
	 * Generate data set including ratings and trust
	 * 
	 * @param dirPath
	 * @throws Exception
	 */
	public static void generateDataset(String dirPath) throws Exception
	{
		File dir = new File(dirPath);
		if (dir.exists() && dir.isDirectory())
		{
			BufferedWriter bw_ratings = new BufferedWriter(new FileWriter(new File("ratings.txt")));
			BufferedWriter bw_trust = new BufferedWriter(new FileWriter(new File("trust.txt")));
			
			File[] files = dir.listFiles();
			for (File file : files)
			{
				Model model = ModelFactory.createDefaultModel();
				try
				{
					model.read("file:///" + file.getAbsolutePath());
				} catch (Exception e)
				{
					System.out.println(file.getAbsolutePath());
					e.printStackTrace();
					continue;
				}

				StmtIterator stmtIterator = model.listStatements();
				while (stmtIterator.hasNext())
				{
					Statement stmt = stmtIterator.next();

					Resource subject = stmt.getSubject();
					Property predicate = stmt.getPredicate();
					RDFNode object = stmt.getObject();

					String subjectName = subject.getLocalName();
					String propertyName = predicate.getLocalName();
					if (propertyName.equalsIgnoreCase("rating"))
					{
						if (!subjectName.contains(file.getName().replaceAll(".rdf", "")))
						{
							String uri = subject.getURI();
							int index = uri.indexOf("#");
							subjectName = uri.substring(index + 1);
						}
						String data[] = subjectName.split("-");
						if (data.length < 2) continue;

						String movieId = data[0];
						String userId = data[1];
						String rating = userId + "::" + movieId + "::" + object.toString() + "\n";
						bw_ratings.write(rating);
					} else if (propertyName.equalsIgnoreCase("knows"))
					{
						String userA = subjectName;
						String userB = object.asResource().getLocalName();
						String trust = userA + "::" + userB + "::1" + "\n";
						bw_trust.write(trust);
					}

				}
			}

			bw_ratings.close();
			bw_trust.close();
		}
	}
}
