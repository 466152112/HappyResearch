package librec.undefined;

import happy.coding.math.Stats;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import librec.data.SparseMatrix;
import librec.data.SparseVector;
import librec.data.VectorEntry;
import librec.ranking.WRMF;

public class RBMF extends WRMF {

	public RBMF(SparseMatrix rm, SparseMatrix tm, int fold) {
		super(rm, tm, fold);

		algoName = "RBMF"; // ranking-based matrix factorization
		isRankingPred = true;
	}

	protected double ranking(int u, int j) {

		double sum = 0;
		for (int f = 0; f < numFactors; f++) {
			double puf = P.get(u, f);
			double qjf = Q.get(j, f);
			if (puf > 0)
				sum += puf * puf * qjf;
		}

		return sum;
	}

	protected Map<Integer, Double> ranking2(int u, Collection<Integer> candItems) {
		SparseVector Ru = trainMatrix.row(u);

		// learn the core features
		List<Integer> fs = new ArrayList<>();
		for (int f = 0; f < numFactors; f++) {
			double[] fvals = new double[Ru.getCount()];
			int i = 0;
			for (VectorEntry ve : Ru) {
				int j = ve.index();
				fvals[i++] = Q.get(j, f);
			}

			double mean = Stats.mean(fvals);
			double std = Stats.sd(fvals);
			if (std < 0.5 || std > 2.5)
				fs.add(f);
		}

		// find items with high core features
		Map<Integer, Double> ranks = new HashMap<>();
		for (int j : candItems) {
			double rank = 0;
			for (int f : fs) {
				rank += P.get(u, f) * Q.get(j, f);
			}
			ranks.put(j, rank);
		}

		return ranks;
	}

}
