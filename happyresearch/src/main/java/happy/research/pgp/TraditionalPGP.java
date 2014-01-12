package happy.research.pgp;

import java.util.List;
import java.util.Map.Entry;

public class TraditionalPGP extends AbstractPGP
{
	
	@Override
	protected Performance firePerformanceTest(final List<PGPNode> nodes)
	{
		int numNodes = nodes.size();
		int coversCount[] = new int[numNodes];
		double coverages[] = new double[numNodes];
		double accuracies[] = new double[numNodes];
		double distances[] = new double[numNodes];
		
		for (int i = 0; i < numNodes; i++)
		{
			PGPNode entity = nodes.get(i);
			
			int coverageCount = 0, accuracyCount = 0;
			for (int j = 0; j < numNodes; j++)
			{
				if (j == i) continue;
				PGPNode target = nodes.get(j);
				if (entity.getTns().containsKey(target)) continue;
				
				int numCompletes = 0, numMarginals = 0;
				for (Entry<PGPNode, CertificateType> entry : target.getSigners().entrySet())
				{
					PGPNode signer = entry.getKey();
					CertificateType ct = entry.getValue();
					if (target == signer) continue;

					if (ct == CertificateType.VALID)
					{
						if (entity.getTns().containsKey(signer))
						{
							TrustType trustness = entity.getTns().get(signer);
							if (trustness == TrustType.COMPLETED_TRUST)
								numCompletes++;
							else if (trustness == TrustType.MARGINALLY_TRUST)
								numMarginals++;
						}
					}
				}

				double distance = 0.0;
				if (numCompletes == 0 && numMarginals == 0)
					continue;// unknown since no tn signed this target
				else if (numCompletes >= AbstractPGP.COMPLETES_NEEDED || numMarginals >= AbstractPGP.MARGINALS_NEEDED)
				{
					coverageCount++;
					distance = Math.abs(CertificateType.VALID.getInherentValue()
							- target.getCertificate().getInherentValue());

				} else if (numCompletes > 0 || numMarginals > 0)
				{
					coverageCount++;
					distance = Math.abs(CertificateType.INVALID.getInherentValue()
							- target.getCertificate().getInherentValue());
				}
				distances[i] += distance;
				if (distance <= ACCURACY_THRESHOLD) accuracyCount++;
				logger.debug("Entity " + i + "'s distance for target " + j + " = " + distance);
			}
			coversCount[i] = coverageCount;
			int needsCoverCount = numNodes - 1 - entity.getTns().size();
			coverages[i] = (coverageCount + 0.0) / needsCoverCount;
			accuracies[i] = (accuracyCount + 0.0) / needsCoverCount;
			if (coversCount[i] > 0)
				distances[i] /= coversCount[i];

		}
		
		return calculatePerformance(AbstractPGP.TRADITIONAL_PGP, coversCount, coverages, distances, accuracies);
	}
	
}
