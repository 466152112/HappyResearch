package happy.research.pgp;

import java.util.List;
import java.util.Map.Entry;

public class ModifiedPGP extends AbstractPGP
{
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
			
			int coverageCount = 0;
			int accuracy_count = 0;
			for (int j = 0; j < numNodes; j++)
			{
				if (j == i) continue;

				double positiveSum = 0, negativeSum = 0;
				int count = 0;

				PGPNode target = nodes.get(j);
				if (entity.getTns().containsKey(target)) continue;
				
				for (Entry<PGPNode, CertificateType> entry : target.getSigners().entrySet())
				{
					PGPNode signer = entry.getKey();
					CertificateType validity = entry.getValue();
					if (target == signer) continue;
					if (entity.getTns().containsKey(signer))
					{
						count++;
						TrustType trustness = entity.getTns().get(signer);
						if (validity == CertificateType.VALID)
						{
							positiveSum += trustness.getTrustValue() * validity.getInherentValue();
						} else if (validity == CertificateType.INVALID)
						{
							negativeSum += trustness.getTrustValue() * validity.getInherentValue();
						}
					}
				}

				if (count > 0)
				{
					coverageCount++;
					double validity = (Math.abs(positiveSum) - Math.abs(negativeSum))
							/ (Math.abs(positiveSum) + Math.abs(negativeSum));
					double distance = Math.abs(validity - target.getCertificate().getInherentValue());
					distances[i] += distance;
					if (distance <= ACCURACY_THRESHOLD) accuracy_count++;
				}
			}
			// coverage and distance test
			coversCount[i] = coverageCount;
			int needsCoversCount = numNodes - 1 - entity.getTns().size();
			coverages[i] = (coversCount[i] + 0.0) / needsCoversCount;
			accuracies[i] = (accuracy_count + 0.0) / needsCoversCount;
			if (coversCount[i] > 0)
				distances[i] /= coversCount[i];
		}
		
		return calculatePerformance(AbstractPGP.MODIFIED_PGP, coversCount, coverages, distances, accuracies);
	}
}
