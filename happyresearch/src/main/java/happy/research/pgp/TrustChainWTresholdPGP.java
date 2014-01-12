package happy.research.pgp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

public class TrustChainWTresholdPGP extends AbstractPGP {
	protected Performance firePerformanceTest(List<PGPNode> nodes) {
		int numNodes = nodes.size();
		int[] coversCount = new int[numNodes];
		double[] coverages = new double[numNodes];
		double[] accuracies = new double[numNodes];
		double[] distances = new double[numNodes];
		double chain_trustness;
		for (int i = 0; i < numNodes; i++) {
			PGPNode entity = (PGPNode) nodes.get(i);
			entity.getNns().clear();
			for (int j = 0; j < numNodes; j++) {
				if (j == i)
					continue;
				PGPNode target = (PGPNode) nodes.get(j);
				if (entity.getTns().containsKey(target)) {
					continue;
				}
				List<List<Integer>> chains = findChains(entity, target, 4);
				if ((chains == null) || (chains.size() <= 0)) {
					continue;
				}

				double chains_sum = 0.0D;
				int count = 0;
				for (List<Integer> chain : chains) {
					PGPNode parentNode = entity;
					chain_trustness = 1.0D;
					for (int k = 1; k < chain.size(); k++) {
						PGPNode currentNode = (PGPNode) nodes
								.get(((Integer) chain.get(k)).intValue());
						TrustType tt = (TrustType) parentNode.getNeighborus()
								.get(currentNode);

						chain_trustness *= tt.getTrustValue();
						parentNode = currentNode;
					}
					if (chain_trustness <= 0.0D)
						continue;
					count++;
					chains_sum += chain_trustness;
				}

				if (count != 0) {
					double trust = chains_sum / count;
					if (trust >= AbstractPGP.CHAIN_TRUSTVALUE_THRESHOLD)
						entity.getNns().put(target, Double.valueOf(trust));
				}
			}

		}

		for (int i = 0; i < numNodes; i++) {
			PGPNode entity = (PGPNode) nodes.get(i);
			int coverage_count = 0;
			int accuracy_count = 0;
			for (int j = 0; j < numNodes; j++) {
				if (j != i) {
					PGPNode target = (PGPNode) nodes.get(j);
					if ((entity.getTns().containsKey(target))
							|| (entity.getNns().containsKey(target)))
						continue;
					double positive_sum = 0.0D;
					double negative_sum = 0.0D;
					int count = 0;
					for (Entry<PGPNode, CertificateType> entry : target
							.getSigners().entrySet()) {
						PGPNode signer = (PGPNode) entry.getKey();
						CertificateType ct = (CertificateType) entry.getValue();

						double trust = 0.0D;
						if (entity.getTns().containsKey(signer)) {
							trust = ((TrustType) entity.getTns().get(signer))
									.getTrustValue();
						} else {
							if (!entity.getNns().containsKey(signer))
								continue;
							trust = ((Double) entity.getNns().get(signer))
									.doubleValue();
						}

						if (trust <= 0.0D)
							continue;
						count++;
						if (ct == CertificateType.VALID)
							positive_sum += trust * ct.getInherentValue();
						else if (ct == CertificateType.INVALID) {
							negative_sum += trust * ct.getInherentValue();
						}
					}
					if (count <= 0)
						continue;
					coverage_count++;
					double validity = (Math.abs(positive_sum) - Math
							.abs(negative_sum))
							/ (Math.abs(positive_sum) + Math.abs(negative_sum));
					double distance = Math.abs(validity
							- target.getCertificate().getInherentValue());
					distances[i] += distance;
					if (distance <= ACCURACY_THRESHOLD)
						accuracy_count++;
					logger.debug("Entity " + i + "'s distance to target " + j
							+ " = " + distance);
				}
			}
			coversCount[i] = coverage_count;
			int needsCoverCount = numNodes - 1 - entity.getTns().size()
					- entity.getNns().size();
			coverages[i] = ((coversCount[i] + 0.0D) / needsCoverCount);
			accuracies[i] = ((accuracy_count + 0.0D) / needsCoverCount);
			if (coversCount[i] > 0) {
				distances[i] /= coversCount[i];
			}
		}
		return calculatePerformance(AbstractPGP.TRUSTCHAINWTHRESHOLD_PGP,
				coversCount, coverages, distances, accuracies);
	}

	protected List<List<Integer>> findChains(PGPNode entity, PGPNode target,
			int maxDepth) {
		List<List<Integer>> chains = new ArrayList<>();

		for (Entry<PGPNode, TrustType> entry : entity.getTns().entrySet()) {
			PGPNode tn = (PGPNode) entry.getKey();

			List<PGPNode> exceptions = new ArrayList<>();
			exceptions.add(entity);
			exceptions.addAll(entity.getTns().keySet());

			List<List<Integer>> subchains = findChainPaths(tn, target,
					maxDepth - 1, exceptions);
			for (List<Integer> subchain : subchains) {
				List<Integer> chain = new ArrayList<>();
				chain.add(Integer.valueOf(entity.getKeyId()));
				chain.addAll(subchain);

				chains.add(chain);
			}
		}
		return chains;
	}

	protected void printChains(List<List<Integer>> chains) {
		for (List<Integer> chain : chains) {
			for (int j = 0; j < chain.size(); j++) {
				if (j == chain.size() - 1)
					System.out.print(chain.get(j));
				else
					System.out.print(chain.get(j) + " --> ");
			}
			System.out.println();
		}
	}

	private List<List<Integer>> findChainPaths(PGPNode entity, PGPNode target,
			int maxDepth, List<PGPNode> exceptions) {
		if (maxDepth <= 0)
			return null;
		List<List<Integer>> chains = new ArrayList<>();

		for (Entry<PGPNode, TrustType> entry : entity.getTns().entrySet()) {
			PGPNode tn = (PGPNode) entry.getKey();
			if (!exceptions.contains(tn)) {
				List<Integer> chain = new ArrayList<>();

				if (target.getSigners().containsKey(tn)) {
					chain.add(Integer.valueOf(entity.getKeyId()));
					chain.add(Integer.valueOf(tn.getKeyId()));
					chain.add(Integer.valueOf(target.getKeyId()));
					chains.add(chain);
				} else {
					if (maxDepth - 1 <= 0) {
						return chains;
					}

					chain.add(Integer.valueOf(entity.getKeyId()));
					exceptions.add(tn);
					List<List<Integer>> subchains = findChainPaths(tn, target,
							maxDepth - 1, exceptions);

					if ((subchains == null) || (subchains.size() <= 0))
						continue;
					for (int i = 0; i < subchains.size(); i++) {
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