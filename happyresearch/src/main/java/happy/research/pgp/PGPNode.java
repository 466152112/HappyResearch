package happy.research.pgp;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

public class PGPNode implements Comparable<PGPNode>
{
	private int keyId;
	private CertificateType certificate;
	
	public static enum SignerType
	{
		ExperiencedSigner, MediumSigner, NewbieSigner
	};
	
	public static enum HonestType
	{
		Honest, Dishonest, Neutral
	};
	
	public static enum MistakeSingerType {
		Many, Neutral, Little
	}
	
	private SignerType signerType;
	private HonestType honestType;
	private MistakeSingerType mistakeType;

	private static Logger logger = Logger.getLogger(PGPNode.class);

	/* <PGPNode, Double> = <target signed by this, trust value Be(p)> */
	/* stores the targets that the current node signed */
	private Map<PGPNode, TrustType> neighborus;
	private Map<PGPNode, TrustType> tns; // Trusted Neighbours
	private Map<PGPNode, Double> nns; // Nearest Neighbours: trust value=similarity

	/* stores who signed the current node */
	private Map<PGPNode, CertificateType> signers;

	public PGPNode(int keyId, CertificateType validity)
	{
		this.keyId = keyId;
		this.certificate = validity;
		this.honestType = HonestType.Honest;
		neighborus = new HashMap<>();
		tns = new HashMap<>();
		nns = new HashMap<>();
		signers = new HashMap<>();
		
		// self signing
		this.signTarget(this, CertificateType.VALID, TrustType.COMPLETED_TRUST);
	}
	
	public void signTarget(PGPNode target, CertificateType validity, TrustType trustness)
	{
		if (target.getSigners().containsKey(this))
		{
			logger.warn("Target is already signed before.");
		} else
		{
			target.getSigners().put(this, validity);
			neighborus.put(target, trustness);
			if (target != this && trustness.getTrustValue() >= 0.5) tns.put(target, trustness);
		}
	}
	
	public void addSpecifiedTarget(PGPNode target, TrustType trustness)
	{
		neighborus.put(target, trustness);
		tns.put(target, trustness);
	}

	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Key id = ")
			.append(keyId)
			.append(" \tCertificate type = ")
			.append(certificate.name())
			.append(" \tHonest type = ")
			.append(honestType.name())
			.append(" \tSigner type = ")
			.append(signerType.name())
			.append("\nNeighbours[")
			.append(neighborus.size())
			.append("]:\n");

		int count = 0;
		int sep = 8;
		for (Entry<PGPNode, TrustType> entry : neighborus.entrySet())
		{
			count++;
			sb.append(entry.getKey().getKeyId());
			sb.append("(");
			sb.append(entry.getValue().getTrustValue());
			sb.append(",").append(entry.getKey().getCertificate().getInherentValue());
			sb.append(")");
			if (count % sep == 0)
				sb.append("\n");
			else
				sb.append(" \t");
		}
		sb.append("\nTrusted Neighbours[");
		sb.append(tns.size());
		sb.append("]:\n");
		count = 0;
		for (Entry<PGPNode, TrustType> entry : tns.entrySet())
		{
			count++;
			sb.append(entry.getKey().getKeyId());
			sb.append("(").append(entry.getValue().getTrustValue());
			sb.append(",").append(entry.getKey().getCertificate().getInherentValue());
			sb.append(")");
			if (count % sep == 0)
				sb.append("\n");
			else
				sb.append(" \t");
		}
		sb.append("\nSigners[");
		sb.append(signers.size());
		sb.append("]:\n");
		count = 0;
		for (Entry<PGPNode, CertificateType> entry : signers.entrySet())
		{
			count++;
			sb.append(entry.getKey().getKeyId());
			sb.append("(");
			sb.append(entry.getValue().getInherentValue());
			sb.append(")");
			if (count % (sep + 5) == 0)
				sb.append("\n");
			else
				sb.append(" \t");
		}
		sb.append("\n--------------------------------------------------------------------------------------------------------------------\n");

		return sb.toString();
	}

	public int getKeyId()
	{
		return keyId;
	}

	public CertificateType getCertificate()
	{
		return certificate;
	}

	public void setCertificate(CertificateType certificate)
	{
		this.certificate = certificate;
	}

	public Map<PGPNode, CertificateType> getSigners()
	{
		return signers;
	}

	public void setSigners(Map<PGPNode, CertificateType> signers)
	{
		this.signers = signers;
	}

	public Map<PGPNode, TrustType> getNeighborus()
	{
		return neighborus;
	}

	public void setNeighborus(Map<PGPNode, TrustType> neighborus)
	{
		this.neighborus = neighborus;
	}

	public Map<PGPNode, TrustType> getTns()
	{
		return tns;
	}

	public void setTns(Map<PGPNode, TrustType> tns)
	{
		this.tns = tns;
	}

	public void setKeyId(int keyId)
	{
		this.keyId = keyId;
	}
	
	public int compareTo(PGPNode target)
	{
		return (this.keyId - target.getKeyId());
	}
	
	public SignerType getSignerType()
	{
		return signerType;
	}
	
	public void setSignerType(SignerType signerType)
	{
		this.signerType = signerType;
	}
	
	public HonestType getHonestType()
	{
		return honestType;
	}
	
	public void setHonestType(HonestType honestType)
	{
		this.honestType = honestType;
	}
	
	public void setMistakeType(MistakeSingerType mistakeType) {
		this.mistakeType = mistakeType;
	}

	public Map<PGPNode, Double> getNns()
	{
		return nns;
	}
	
	public void setNns(Map<PGPNode, Double> nns)
	{
		this.nns = nns;
	}

	public MistakeSingerType getMistakeType() {
		return mistakeType;
	}
}
