package happy.research.pgp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class OurMethodWChain extends TrustChainPGP
{
	
	@Override
	protected Performance firePerformanceTest(List<PGPNode> nodes)
	{
		int numNodes = AbstractPGP.numNodes;
		
		// compute trust values
		for (int i = 0; i < numNodes; i++)
		{
			PGPNode entity = nodes.get(i);
			// clear entity's nns
			entity.getNns().clear();
			
			for (int j = 0; j < numNodes; j++)
			{
				if (j == i) continue;
				PGPNode target = nodes.get(j);
				if (entity.getTns().containsKey(target)) continue;
				
				// find chains between entity and target
				List<List<Integer>> chains = findChains(entity, target, 4);
				if (chains == null || chains.size() <= 0) continue;
				
				PGPNode parentNode, currentNode;
				double chains_sum = 0.0;
				int count = 0;
				for (List<Integer> chain : chains)
				{
					parentNode = entity;
					double chain_trustness = 1.0;
					for (int k = 1; k < chain.size(); k++)
					{
						currentNode = nodes.get(chain.get(k));
						TrustType tt = parentNode.getNeighborus().get(currentNode);
						
						chain_trustness *= tt.getTrustValue();
						parentNode = currentNode;
					}
					if (chain_trustness > 0)
					{
						count++;
						chains_sum += chain_trustness;
					}
				}
				if (count == 0) continue;
				double trust = chains_sum / count;
				if (trust >= AbstractPGP.COMPLETE_TRUST_THRESHOLD)
					entity.getNns().put(target, TrustType.COMPLETED_TRUST.getTrustValue());
				else if (trust >= AbstractPGP.MARGINAL_TRUST_THRESHOLD)
					entity.getNns().put(target, TrustType.MARGINALLY_TRUST.getTrustValue());
			}
			
		}
		
		int coversCount[] = new int[numNodes];
		double coverages[] = new double[numNodes];
		double accuracies[] = new double[numNodes];
		double distances[] = new double[numNodes];
		
		for (int i = 0; i < numNodes; i++)
		{
			PGPNode entity = nodes.get(i);
			Map<PGPNode, Double> tnsAll_positive = new HashMap<>(numNodes * 2);
			Map<PGPNode, Double> tnsAll_negative = new HashMap<>(numNodes * 2);
			Map<PGPNode, CertificateType> tnsAll_combined = new HashMap<>(numNodes * 2);
			Map<PGPNode, Double> tnsAll_confidence = new HashMap<>(numNodes * 2);
			// 1. process trusted neighbours
			for (Entry<PGPNode, TrustType> entry : entity.getTns().entrySet())
			{
				PGPNode tn = entry.getKey();
				TrustType tt = entry.getValue();
				if (tn == entity) continue;
				for (Entry<PGPNode, TrustType> tn_entry : tn.getNeighborus().entrySet())
				{
					PGPNode tn_tn = tn_entry.getKey();
					CertificateType tn_ct = tn_tn.getSigners().get(tn);
					
					// Due to some tns directly predefined without signing
					// so tn_ct == null is possible
					if (tn_ct == null) continue;
					
					if (tnsAll_positive.containsKey(tn_tn))
					{
						// combine together using majority method
						// problem: what if positive no. = negative no.?
						
						if (tn_ct == CertificateType.VALID)
						{
							tnsAll_positive.put(
									tn_tn,
									tnsAll_positive.get(tn_tn).doubleValue() + tn_ct.getInherentValue()
											* tt.getTrustValue());
							
						} else if (tn_ct == CertificateType.INVALID)
						{
							tnsAll_negative.put(
									tn_tn,
									tnsAll_negative.get(tn_tn).doubleValue() + tn_ct.getInherentValue()
											* tt.getTrustValue());
						}
						
					} else
					{
						// make sure positive and negative set have same size
						if (tn_ct == CertificateType.VALID)
						{
							tnsAll_positive.put(tn_tn, tn_ct.getInherentValue() * tt.getTrustValue());
							tnsAll_negative.put(tn_tn, 0.0);
						} else if (tn_ct == CertificateType.INVALID)
						{
							tnsAll_positive.put(tn_tn, 0.0);
							tnsAll_negative.put(tn_tn, tn_ct.getInherentValue() * tt.getTrustValue());
						}
					}
				}
			}
			// 2. process nearest neighbours
			for (Entry<PGPNode, Double> entry : entity.getNns().entrySet())
			{
				PGPNode nn = entry.getKey();
				double trustnesss = entry.getValue().doubleValue();
				if (nn == entity) continue;
				for (Entry<PGPNode, TrustType> nn_entry : nn.getNeighborus().entrySet())
				{
					PGPNode nn_nb = nn_entry.getKey();
					CertificateType nn_ct = nn_nb.getSigners().get(nn);
					
					if (nn_ct == null) continue;
					
					if (tnsAll_positive.containsKey(nn_nb))
					{
						if (nn_ct == CertificateType.VALID)
						{
							tnsAll_positive.put(nn_nb,
									tnsAll_positive.get(nn_nb).doubleValue() + nn_ct.getInherentValue() * trustnesss);
							
						} else if (nn_ct == CertificateType.INVALID)
						{
							tnsAll_negative.put(nn_nb,
									tnsAll_negative.get(nn_nb).doubleValue() + nn_ct.getInherentValue() * trustnesss);
						}
						
					} else
					{
						// make sure positive and negative set have same size
						if (nn_ct == CertificateType.VALID)
						{
							tnsAll_positive.put(nn_nb, nn_ct.getInherentValue() * trustnesss);
							tnsAll_negative.put(nn_nb, 0.0);
						} else if (nn_ct == CertificateType.INVALID)
						{
							tnsAll_positive.put(nn_nb, 0.0);
							tnsAll_negative.put(nn_nb, nn_ct.getInherentValue() * trustnesss);
						}
					}
				}
			}
			// compute combined vector and confidence
			for (Entry<PGPNode, Double> entry_positive : tnsAll_positive.entrySet())
			{
				PGPNode node = entry_positive.getKey();
				double r = Math.abs(entry_positive.getValue().doubleValue());
				double s = Math.abs(tnsAll_negative.get(node).doubleValue());
				try
				{
					double confidence = calculateCertainty(r, s);
					if (confidence >= AbstractPGP.CONFIDENCE_THRESHOLD && r > s)
					{
						tnsAll_combined.put(node, CertificateType.VALID);
						tnsAll_confidence.put(node, new Double(confidence));
					} else if (confidence >= AbstractPGP.CONFIDENCE_THRESHOLD && r <= s)
					{
						tnsAll_combined.put(node, CertificateType.INVALID);
						tnsAll_confidence.put(node, new Double(confidence));
					}
				} catch (Exception e)
				{
					e.printStackTrace();
				}
			}
			// compute the similar nodes
			if (tnsAll_combined.size() > 0)
			{
				for (int j = 0; j < numNodes; j++)
				{
					if (j == i) continue;// self
					PGPNode candidate = nodes.get(j);
					if (entity.getTns().containsKey(candidate)) continue; // tns
					if (entity.getNns().containsKey(candidate)) continue; // nns
						
					// test if this node is a candidate or not
					int size = tnsAll_combined.size();
					int[] rxs = new int[size];
					int[] rys = new int[size];
					int index = 0;
					for (Entry<PGPNode, CertificateType> entry : tnsAll_combined.entrySet())
					{
						PGPNode node = entry.getKey();
						if (candidate.getNeighborus().containsKey(node) && node.getSigners().get(candidate) != null)
						{
							rxs[index] = entry.getValue().getInherentValue();
							rys[index] = node.getSigners().get(candidate).getInherentValue();
							index++;
						}
					}
					if (index <= size * AbstractPGP.CANDIDATE_VECTOR_RATIO)
						continue;
					// calculate similarity
					
					double similarity = evaluateCosineSimilarity(rxs, rys, index);
					// logger.info("test similarity = " + similarity);
					if (similarity >= AbstractPGP.SIMILARITY_THRESHOLD)
						entity.getNns().put(candidate, new Double(similarity));
				}
			}
			// evaluate the performance: coverage and distance
			int count_coverage = 0, accuracy_count = 0;
			for (int j = 0; j < numNodes; j++)
			{
				if (j == i) continue;
				PGPNode testNode = nodes.get(j);
				if (entity.getTns().containsKey(testNode)) continue;
				if (entity.getNns().containsKey(testNode)) continue;
				double positive_sum = 0.0, negative_sum = 0.0;
				int count_test = 0;
				for (Entry<PGPNode, CertificateType> entry : testNode.getSigners().entrySet())
				{
					PGPNode signer = entry.getKey();
					CertificateType ct = entry.getValue();
					
					if (signer == testNode) continue;
					
					if (entity.getTns().containsKey(signer))
					{
						TrustType tt = entity.getTns().get(signer);
						if (ct == CertificateType.VALID)
							positive_sum += tt.getTrustValue() * ct.getInherentValue();
						else if (ct == CertificateType.INVALID)
							negative_sum += tt.getTrustValue() * ct.getInherentValue();
						count_test++;
					} else if (entity.getNns().containsKey(signer))
					{
						double similarity = entity.getNns().get(signer);
						if (ct == CertificateType.VALID)
							positive_sum += similarity * ct.getInherentValue();
						else if (ct == CertificateType.INVALID) negative_sum += similarity * ct.getInherentValue();
						count_test++;
					}
				}
				if (count_test > 0)
				{
					count_coverage++;
					double validity = (Math.abs(positive_sum) - Math.abs(negative_sum))
							/ (Math.abs(positive_sum) + Math.abs(negative_sum));
					double distance = Math.abs(validity - testNode.getCertificate().getInherentValue());
					distances[i] += distance;
					if (distance <= ACCURACY_THRESHOLD) accuracy_count++;
				}
			}
			coversCount[i] = count_coverage;
			int needsCoverCount = numNodes - 1 - entity.getTns().size() - entity.getNns().size();
			coverages[i] = (coversCount[i] + 0.0) / needsCoverCount;
			accuracies[i] = (accuracy_count + 0.0) / needsCoverCount;
			if (coversCount[i] > 0) distances[i] /= coversCount[i];
		}
		
		return calculatePerformance(AbstractPGP.OURMETHOD_CHAIN_PGP, coversCount, coverages, distances, accuracies);
	}
	

}
