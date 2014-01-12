package happy.research.pgp;

import java.util.List;
import java.util.Map.Entry;

/**
 * Traditional PGP with chain of trust
 * 
 * @author Felix
 * 
 */
public class TraditionalWChainPGP extends TrustChainPGP
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
				if (chains == null || chains.size() == 0)
					continue;
				
				// printChains(chains);
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
				// logger.info("entity " + i + "'s trust for target " + j +
				// " = " + trust);

				if (trust >= AbstractPGP.COMPLETE_TRUST_THRESHOLD)
					entity.getNns().put(target, TrustType.COMPLETED_TRUST.getTrustValue());
				else if (trust >= AbstractPGP.MARGINAL_TRUST_THRESHOLD)
					entity.getNns().put(target, TrustType.MARGINALLY_TRUST.getTrustValue());
			}
			
		}
		
		// compute performance
		int coversCount[] = new int[numNodes];
		double coverages[] = new double[numNodes];
		double accuracies[] = new double[numNodes];
		double distances[] = new double[numNodes];

		for (int i = 0; i < numNodes; i++)
		{
			PGPNode entity = nodes.get(i);
			int coverage_count = 0, accuracy_count = 0;
			for (int j = 0; j < numNodes; j++)
			{
				if (j == i) continue;
				PGPNode target = nodes.get(j);
				if (entity.getTns().containsKey(target)) continue;
				if (entity.getNns().containsKey(target)) continue;
				
				int numCompletes = 0, numMarginals = 0;
				for (Entry<PGPNode, CertificateType> entry : target.getSigners().entrySet())
				{
					PGPNode signer = entry.getKey();
					CertificateType ct = entry.getValue();
					
					// only valid certificates will be considered
					if (ct == CertificateType.VALID)
					{
						if (entity.getTns().containsKey(signer))
						{
							TrustType tt = entity.getTns().get(signer);
							if (tt == TrustType.COMPLETED_TRUST)
								numCompletes++;
							else if (tt == TrustType.MARGINALLY_TRUST) 
								numMarginals++;
						} else if (entity.getNns().containsKey(signer))
						{
							double trust = entity.getNns().get(signer).doubleValue();
							if (trust == TrustType.COMPLETED_TRUST.getTrustValue())
								numCompletes++;
							else if (trust == TrustType.MARGINALLY_TRUST.getTrustValue()) 
								numMarginals++;
						}
					}
				}
				
				double distance = 0.0;
				if (numCompletes == 0 && numMarginals == 0)
					continue;// unknown since no tn signed this target
				else if (numCompletes >= AbstractPGP.COMPLETES_NEEDED || numMarginals >= AbstractPGP.MARGINALS_NEEDED)
				{
					distance = Math.abs(CertificateType.VALID.getInherentValue()
							- target.getCertificate().getInherentValue());
					
				} else if (numCompletes > 0 || numMarginals > 0)
				{
					distance = Math.abs(CertificateType.INVALID.getInherentValue()
							- target.getCertificate().getInherentValue());
				}
				coverage_count++;
				distances[i] += distance;
				if (distance <= ACCURACY_THRESHOLD) accuracy_count++;
			}
			coversCount[i] = coverage_count;
			int needsCoverCount = numNodes - 1 - entity.getTns().size() - entity.getNns().size();
			// needsCoverCount[i] = numNodes - 1 - entity.getTns().size();
			coverages[i] = (coversCount[i] + 0.0) / needsCoverCount;
			accuracies[i] = (accuracy_count + 0.0) / needsCoverCount;
			if (coversCount[i] > 0)
				distances[i] /= coversCount[i];
		}
		
		return calculatePerformance(AbstractPGP.TRADITIONAL_CHAIN_PGP, coversCount, coverages, distances, accuracies);
	}
	
}
