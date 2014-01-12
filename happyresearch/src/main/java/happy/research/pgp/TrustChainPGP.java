package happy.research.pgp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

public class TrustChainPGP extends AbstractPGP
{
	
	protected Performance firePerformanceTest(final List<PGPNode> nodes)
	{
		int numNodes = nodes.size();
		int coversCount[] = new int[numNodes];
		double coverages[] = new double[numNodes];
		double accuracies[] = new double[numNodes];
		double distances[] = new double[numNodes];
		
		// compute trust values
		double trusts[][] = new double[numNodes][numNodes];
		for (int i = 0; i < numNodes; i++)
		{
			PGPNode entity = nodes.get(i);
			for (int j = 0; j < numNodes; j++)
			{
				if (j == i)
				{
					trusts[i][j] = TrustType.COMPLETED_TRUST.getTrustValue();
					continue;
				}
				PGPNode target = nodes.get(j);
				if (entity.getTns().containsKey(target))
				{
					trusts[i][j] = entity.getTns().get(target).getTrustValue();
					continue;
				}
				
				// find chains between entity and target
				List<List<Integer>> chains = findChains(entity, target, 4);
				if (chains == null || chains.size() <= 0)
				{
					trusts[i][j] = -1.0;
					continue;
				}
				
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
				
				trusts[i][j] = chains_sum / count;
			}

		}
		
		// compute validity
		for (int i = 0; i < numNodes; i++)
		{
			PGPNode entity = nodes.get(i);
			int coverage_count = 0, accuracy_count = 0;
			for (int j = 0; j < numNodes; j++)
			{
				if (j == i) continue;
				PGPNode target = nodes.get(j);
				if (entity.getTns().containsKey(target)) continue;
				
				double positive_sum = 0.0, negative_sum = 0.0;
				int count = 0;
				for (Entry<PGPNode, CertificateType> entry : target.getSigners().entrySet())
				{
					PGPNode signer = entry.getKey();
					CertificateType ct = entry.getValue();
					
					double trust = trusts[i][signer.getKeyId()];
					if (trust > 0)
					{
						count++;
						if (ct == CertificateType.VALID)
							positive_sum += trust * ct.getInherentValue();
						else if (ct == CertificateType.INVALID) 
							negative_sum += trust * ct.getInherentValue();
					}
				}
				if (count > 0)
				{
					coverage_count++;
					double validity = (Math.abs(positive_sum) - Math.abs(negative_sum))
							/ (Math.abs(positive_sum) + Math.abs(negative_sum));
					double distance = Math.abs(validity - target.getCertificate().getInherentValue());
					distances[i] += distance;
					if (distance <= ACCURACY_THRESHOLD) accuracy_count++;
					logger.debug("Entity " + i + "'s distance to target " + j + " = " + distance);
				}
			}
			coversCount[i] = coverage_count;
			int needsCoverCount = numNodes - 1 - entity.getTns().size();
			coverages[i] = (coversCount[i] + 0.0) / needsCoverCount;
			accuracies[i] = (accuracy_count + 0.0) / needsCoverCount;
			if (coversCount[i] > 0)
				distances[i] /= coversCount[i];
		}
		
		return calculatePerformance(AbstractPGP.TRUSTCHAIN_PGP, coversCount, coverages, distances, accuracies);
	}

	protected List<List<Integer>> findChains(PGPNode entity, PGPNode target, int maxDepth)
	{
		List<List<Integer>> chains = new ArrayList<>();

		for (Entry<PGPNode, TrustType> entry : entity.getTns().entrySet())
		{
			PGPNode tn = entry.getKey();

			List<PGPNode> exceptions = new ArrayList<>();
			exceptions.add(entity);
			exceptions.addAll(entity.getTns().keySet());

			List<List<Integer>> subchains = findChainPaths(tn, target, maxDepth - 1, exceptions);
			for (List<Integer> subchain : subchains)
			{
				List<Integer> chain = new ArrayList<>();
				chain.add(entity.getKeyId());
				chain.addAll(subchain);
				
				chains.add(chain);
			}
		}
		return chains;
	}
	
	protected void printChains(List<List<Integer>> chains)
	{
		for (List<Integer> chain : chains)
		{
			for (int j = 0; j < chain.size(); j++)
			{
				if (j == chain.size() - 1)
					System.out.print(chain.get(j));
				else
					System.out.print(chain.get(j) + " --> ");
			}
			System.out.println();
		}
	}

	private List<List<Integer>> findChainPaths(PGPNode entity,
									PGPNode target,	int maxDepth, List<PGPNode> exceptions)
	{
		if (maxDepth <= 0) return null;
		List<List<Integer>> chains = new ArrayList<>();

		for (Entry<PGPNode, TrustType> entry : entity.getTns().entrySet())
		{
			PGPNode tn = entry.getKey();
			if (exceptions.contains(tn)) continue;
			List<Integer> chain = new ArrayList<>();
			
			if (target.getSigners().containsKey(tn))
			{
				chain.add(entity.getKeyId());
				chain.add(tn.getKeyId());
				chain.add(target.getKeyId());
				chains.add(chain);
				continue;
			} else if (maxDepth - 1 <= 0)
			{
				return chains;
			} else
			{
				chain.add(entity.getKeyId());
				exceptions.add(tn);
				List<List<Integer>> subchains = findChainPaths(tn, target, maxDepth - 1, exceptions);

				if (subchains != null && subchains.size() > 0)
				{
					for (int i = 0; i < subchains.size(); i++)
					{
						List<Integer> tempchain = new ArrayList<>();
						tempchain.addAll(chain);
						tempchain.addAll(subchains.get(i));
						
						chains.add(tempchain);
					}
				}
			}
		}
		return chains;
	}

}
