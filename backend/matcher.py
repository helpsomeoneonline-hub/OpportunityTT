from __future__ import annotations

from dataclasses import dataclass
from typing import Any


@dataclass
class MatchResult:
    eligible: bool
    score: int
    label: str
    reasons: list[str]
    gaps: list[str]


def _norm(value: str) -> str:
    return " ".join(value.lower().strip().split())


def match_candidate(candidate: dict[str, Any], job: dict[str, Any]) -> MatchResult:
    """Small, explainable MVP matcher. No photo or demographic fields are used."""

    reasons: list[str] = []
    gaps: list[str] = []
    score = 0

    candidate_skills = {_norm(x) for x in candidate.get("skills", [])}
    candidate_quals = {_norm(x) for x in candidate.get("qualifications", [])}
    candidate_licences = {_norm(x) for x in candidate.get("licences", [])}

    # Hard requirements
    for req in job.get("hard_requirements", []):
        req_type = req.get("type")
        value = _norm(req.get("value", ""))

        if req_type == "licence" and value not in candidate_licences:
            gaps.append(f"Missing required licence: {req.get('value')}")
        elif req_type == "qualification" and value not in candidate_quals:
            gaps.append(f"Missing required qualification: {req.get('value')}")
        elif req_type == "skill" and value not in candidate_skills:
            gaps.append(f"Missing required skill: {req.get('value')}")

    if gaps:
        return MatchResult(False, 0, "Not Eligible", reasons, gaps)

    # Job family/category
    preferred_families = {_norm(x) for x in candidate.get("preferred_job_families", [])}
    preferred_categories = {_norm(x) for x in candidate.get("preferred_categories", [])}

    family = _norm(job.get("job_family", ""))
    category = _norm(job.get("category", ""))

    if family and family in preferred_families:
        score += 30
        reasons.append("Preferred job family")
    elif category and category in preferred_categories:
        score += 20
        reasons.append("Preferred job category")

    # Skills
    desired_skills = {_norm(x) for x in job.get("skills", [])}
    if desired_skills:
        overlap = candidate_skills & desired_skills
        ratio = len(overlap) / len(desired_skills)
        score += round(35 * ratio)
        if overlap:
            reasons.append("Skills: " + ", ".join(sorted(overlap)))
        missing_soft = desired_skills - candidate_skills
        if missing_soft:
            gaps.append("Other requested skills: " + ", ".join(sorted(missing_soft)))

    # Experience
    required_years = float(job.get("preferred_years_experience", 0) or 0)
    candidate_years = float(candidate.get("years_experience", 0) or 0)
    if required_years <= 0:
        score += 10
    elif candidate_years >= required_years:
        score += 15
        reasons.append("Meets preferred experience")
    elif candidate_years >= required_years * 0.67:
        score += 8
        gaps.append(
            f"Employer prefers {required_years:g} years; candidate has {candidate_years:g}"
        )

    # Location
    preferred_regions = {_norm(x) for x in candidate.get("preferred_regions", [])}
    region = _norm(job.get("region", ""))
    if region and region in preferred_regions:
        score += 15
        reasons.append("Preferred region")

    # Employment type
    preferred_types = {_norm(x) for x in candidate.get("preferred_employment_types", [])}
    employment_type = _norm(job.get("employment_type", ""))
    if employment_type and employment_type in preferred_types:
        score += 5
        reasons.append("Preferred employment type")

    score = min(score, 100)

    if score >= 75:
        label = "Strong Match"
    elif score >= 50:
        label = "Good Match"
    else:
        label = "Partial Match"

    return MatchResult(True, score, label, reasons, gaps)
