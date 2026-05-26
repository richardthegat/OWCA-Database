-- =========================================================================
-- OWCA Database — 20 Sample Queries
-- File: 03_queries.sql
-- Run AFTER 01 + 02. Each query is preceded by an English description.
-- =========================================================================

SET LINESIZE 200
SET PAGESIZE 50

-- =========================================================================
-- Q1 (MANDATORY).
-- Find all personnel involved in the operation 'Belladonna' who are
-- currently alive, speak Russian, and who lived in London in 2021. List
-- their code names in this operation, their roles in the operation, and
-- their current locations.
-- =========================================================================
PROMPT === Q1 (mandatory): Belladonna alive Russian-speakers who lived in London 2021 ===
SELECT DISTINCT
    a.code_designation,
    a.species,
    asg.op_codename_in_op  AS code_name_in_belladonna,
    asg.role_in_op,
    cl.city || ', ' || cl.country AS current_location
FROM AGENT a
JOIN ASSIGNMENT             asg ON a.agent_id = asg.agent_id
JOIN OPERATION              o   ON asg.operation_id = o.operation_id
JOIN AGENT_LANGUAGE         al  ON a.agent_id = al.agent_id
JOIN LANGUAGE               lng ON al.language_id = lng.language_id
JOIN AGENT_LOCATION_HISTORY h   ON a.agent_id = h.agent_id
JOIN LOCATION               lh  ON h.location_id = lh.location_id
LEFT JOIN LOCATION          cl  ON a.current_location_id = cl.location_id
WHERE o.op_codename = 'Belladonna'
  AND a.is_alive    = 'Y'
  AND lng.language_name = 'Russian'
  AND al.fluency_level IN ('FLUENT','NATIVE')
  AND lh.city = 'London'
  AND h.start_date <= DATE '2021-12-31'
  AND (h.end_date IS NULL OR h.end_date >= DATE '2021-01-01');

-- =========================================================================
-- Q2.  Find all personnel (agents) involved in Operation X17.
-- =========================================================================
PROMPT === Q2: All agents on Operation X17 ===
SELECT a.agent_id, a.code_designation, a.species, asg.role_in_op
FROM AGENT a
JOIN ASSIGNMENT asg ON a.agent_id = asg.agent_id
JOIN OPERATION  o   ON asg.operation_id = o.operation_id
WHERE o.op_codename = 'X17'
ORDER BY asg.role_in_op, a.code_designation;

-- =========================================================================
-- Q3.  Find all personnel whose clearance level allows access to Operation
-- Raven, but who are NOT directly assigned to it.
-- =========================================================================
PROMPT === Q3: Cleared for Raven but not assigned ===
SELECT p.personnel_id, p.first_name || ' ' || p.last_name AS full_name,
       p.role_type, p.clearance_level
FROM PERSONNEL p
WHERE p.clearance_level >= (
        SELECT required_clearance FROM OPERATION WHERE op_codename = 'Operation Raven'
      )
  AND p.personnel_id NOT IN (
        SELECT lead_handler_id FROM OPERATION WHERE op_codename = 'Operation Raven'
      )
ORDER BY p.clearance_level DESC, p.last_name;

-- =========================================================================
-- Q4.  Who knew the true identity behind code name 'Agent P'?
-- =========================================================================
PROMPT === Q4: Personnel who know true identity behind Agent P ===
SELECT p.personnel_id, p.first_name || ' ' || p.last_name AS full_name,
       p.role_type, ki.date_disclosed
FROM KNOWS_IDENTITY ki
JOIN PERSONNEL p ON ki.personnel_id = p.personnel_id
JOIN AGENT     a ON ki.agent_id     = a.agent_id
WHERE a.code_designation = 'Agent P'
ORDER BY ki.date_disclosed;

-- =========================================================================
-- Q5.  Find all analysts who supported missions later classified as
-- compromised.
-- =========================================================================
PROMPT === Q5: Analysts who led compromised operations ===
SELECT DISTINCT p.personnel_id, p.first_name || ' ' || p.last_name AS full_name,
       o.op_codename, o.status
FROM PERSONNEL p
JOIN OPERATION o ON p.personnel_id = o.lead_handler_id
WHERE p.role_type = 'ANALYST'
  AND o.status    = 'COMPROMISED'
ORDER BY o.op_codename;

-- =========================================================================
-- Q6.  Find all operatives who have BOTH paragliding AND karate, and speak
-- fluent or native Russian.
-- =========================================================================
PROMPT === Q6: Paraglider + karate + Russian speakers ===
SELECT a.agent_id, a.code_designation, a.species
FROM AGENT a
WHERE a.agent_id IN (SELECT s.agent_id FROM AGENT_SKILL s
                       JOIN SKILL k ON s.skill_id=k.skill_id
                      WHERE k.skill_name = 'Paragliding')
  AND a.agent_id IN (SELECT s.agent_id FROM AGENT_SKILL s
                       JOIN SKILL k ON s.skill_id=k.skill_id
                      WHERE k.skill_name = 'Karate')
  AND a.agent_id IN (SELECT al.agent_id FROM AGENT_LANGUAGE al
                       JOIN LANGUAGE l ON al.language_id=l.language_id
                      WHERE l.language_name='Russian'
                        AND al.fluency_level IN ('FLUENT','NATIVE'));

-- =========================================================================
-- Q7.  Given two agent code names, find all operations they were both on.
-- (Parameterized example: Agent P and Agent E.)
-- =========================================================================
PROMPT === Q7: Operations both Agent P and Agent E worked on ===
SELECT o.operation_id, o.op_codename, o.status
FROM OPERATION o
WHERE EXISTS (SELECT 1 FROM ASSIGNMENT asg JOIN AGENT a ON asg.agent_id=a.agent_id
              WHERE asg.operation_id = o.operation_id
                AND a.code_designation = 'Agent P')
  AND EXISTS (SELECT 1 FROM ASSIGNMENT asg JOIN AGENT a ON asg.agent_id=a.agent_id
              WHERE asg.operation_id = o.operation_id
                AND a.code_designation = 'Agent E')
ORDER BY o.start_date;

-- =========================================================================
-- Q8.  For each agent, count how many operations they have participated in,
-- and show the most recent operation's name.
-- =========================================================================
PROMPT === Q8: Per-agent operation count and most recent op ===
SELECT a.code_designation,
       a.species,
       COUNT(asg.operation_id) AS total_ops,
       MAX(o.start_date)       AS most_recent_op_start
FROM AGENT a
LEFT JOIN ASSIGNMENT asg ON a.agent_id = asg.agent_id
LEFT JOIN OPERATION  o   ON asg.operation_id = o.operation_id
GROUP BY a.code_designation, a.species
ORDER BY total_ops DESC, a.code_designation;

-- =========================================================================
-- Q9.  Find agents who have NEVER been assigned to any operation.
-- =========================================================================
PROMPT === Q9: Agents with zero assignments ===
SELECT a.agent_id, a.code_designation, a.species, a.date_recruited
FROM AGENT a
WHERE NOT EXISTS (SELECT 1 FROM ASSIGNMENT asg WHERE asg.agent_id = a.agent_id);

-- =========================================================================
-- Q10.  List all locations connected to a compromised operation, along with
-- which operation and what role the location played.
-- =========================================================================
PROMPT === Q10: Locations tied to compromised operations ===
SELECT o.op_codename, l.city, l.country, ol.location_role
FROM OPERATION_LOCATION ol
JOIN OPERATION o ON ol.operation_id = o.operation_id
JOIN LOCATION  l ON ol.location_id  = l.location_id
WHERE o.status = 'COMPROMISED'
ORDER BY o.op_codename, ol.location_role;

-- =========================================================================
-- Q11.  Find agents whose cover identity has been burned.
-- =========================================================================
PROMPT === Q11: Agents with burned covers ===
SELECT a.code_designation, a.species, c.pet_name, c.host_family_name
FROM AGENT a
JOIN COVER_IDENTITY c ON a.cover_id = c.cover_id
WHERE c.is_burned = 'Y';

-- =========================================================================
-- Q12.  For each language, count how many alive agents speak it fluently
-- or natively. Order by most-spoken first.
-- =========================================================================
PROMPT === Q12: Fluent-speaker counts per language (alive agents only) ===
SELECT l.language_name, COUNT(*) AS fluent_speakers
FROM AGENT_LANGUAGE al
JOIN LANGUAGE l ON al.language_id = l.language_id
JOIN AGENT    a ON al.agent_id    = a.agent_id
WHERE al.fluency_level IN ('FLUENT','NATIVE')
  AND a.is_alive = 'Y'
GROUP BY l.language_name
ORDER BY fluent_speakers DESC, l.language_name;

-- =========================================================================
-- Q13.  Find all inators currently classified as ACTIVE, the nemesis who
-- invented them, and the lair that nemesis operates from.
-- =========================================================================
PROMPT === Q13: Active inators with their inventor and lair ===
SELECT i.inator_name, n.villain_name, lair.lair_name, loc.city, loc.country
FROM INATOR i
JOIN NEMESIS  n    ON i.nemesis_id = n.nemesis_id
JOIN LAIR     lair ON n.lair_id    = lair.lair_id
JOIN LOCATION loc  ON lair.location_id = loc.location_id
WHERE i.current_status = 'ACTIVE'
ORDER BY n.nemesis_level DESC, i.inator_name;

-- =========================================================================
-- Q14.  Find all gadgets currently checked out (returned_date IS NULL),
-- which agent has them, and the operation they're checked out for.
-- =========================================================================
PROMPT === Q14: Gadgets currently outstanding ===
SELECT g.gadget_name, g.category, a.code_designation, o.op_codename,
       ga.checked_out_date
FROM GADGET_ASSIGNMENT ga
JOIN GADGET    g ON ga.gadget_id = g.gadget_id
JOIN AGENT     a ON ga.agent_id  = a.agent_id
JOIN OPERATION o ON ga.operation_id = o.operation_id
WHERE ga.returned_date IS NULL
ORDER BY ga.checked_out_date;

-- =========================================================================
-- Q15.  Show each handler and how many active operations they currently
-- lead.
-- =========================================================================
PROMPT === Q15: Handler workload (active ops led) ===
SELECT p.first_name || ' ' || p.last_name AS handler_name,
       p.role_type,
       COUNT(o.operation_id) AS active_ops_led
FROM PERSONNEL p
LEFT JOIN OPERATION o
       ON p.personnel_id = o.lead_handler_id
      AND o.status = 'ACTIVE'
WHERE p.role_type IN ('HANDLER','DIRECTOR')
GROUP BY p.first_name, p.last_name, p.role_type
ORDER BY active_ops_led DESC, handler_name;

-- =========================================================================
-- Q16.  Find every agent who has completed BOTH the 'Advanced Combat'
-- course AND the 'Inator Defense Tactics' course.
-- =========================================================================
PROMPT === Q16: Agents qualified in both Advanced Combat and Inator Defense ===
SELECT a.code_designation, a.species
FROM AGENT a
WHERE a.agent_id IN (SELECT tr.agent_id FROM TRAINING_RECORD tr
                       JOIN TRAINING_COURSE tc ON tr.course_id=tc.course_id
                      WHERE tc.course_name='Advanced Combat')
  AND a.agent_id IN (SELECT tr.agent_id FROM TRAINING_RECORD tr
                       JOIN TRAINING_COURSE tc ON tr.course_id=tc.course_id
                      WHERE tc.course_name='Inator Defense Tactics');

-- =========================================================================
-- Q17.  Recursive: find Carl's full chain of command up to the top
-- (himself, his supervisor, his supervisor's supervisor, …).
-- =========================================================================
PROMPT === Q17: Carl's chain of command (recursive CTE) ===
WITH chain (personnel_id, first_name, last_name, role_type, supervisor_id, lvl) AS (
  SELECT personnel_id, first_name, last_name, role_type, supervisor_id, 0
  FROM PERSONNEL WHERE first_name='Carl'
  UNION ALL
  SELECT p.personnel_id, p.first_name, p.last_name, p.role_type, p.supervisor_id, c.lvl+1
  FROM PERSONNEL p
  JOIN chain c ON p.personnel_id = c.supervisor_id
)
SELECT lvl, personnel_id, first_name || ' ' || last_name AS name, role_type
FROM chain ORDER BY lvl;

-- =========================================================================
-- Q18.  All assets (gadgets and personnel) connected to a compromised
-- operation — useful when an op leaks and we need to do damage assessment.
-- (Combines two queries with UNION.)
-- =========================================================================
PROMPT === Q18: All assets touched by COMPROMISED operations ===
SELECT 'AGENT'   AS asset_type, a.code_designation AS asset_name, o.op_codename
FROM ASSIGNMENT asg
JOIN AGENT     a ON asg.agent_id    = a.agent_id
JOIN OPERATION o ON asg.operation_id = o.operation_id
WHERE o.status = 'COMPROMISED'
UNION
SELECT 'GADGET',  g.gadget_name, o.op_codename
FROM GADGET_ASSIGNMENT ga
JOIN GADGET    g ON ga.gadget_id    = g.gadget_id
JOIN OPERATION o ON ga.operation_id = o.operation_id
WHERE o.status = 'COMPROMISED'
ORDER BY 3, 1, 2;

-- =========================================================================
-- Q19.  Find agents who have higher clearance than their own handler
-- (a security flag worth investigating).
-- =========================================================================
PROMPT === Q19: Agents with higher clearance than their handler ===
SELECT a.code_designation, a.clearance_level AS agent_clearance,
       p.first_name || ' ' || p.last_name AS handler_name,
       p.clearance_level AS handler_clearance
FROM AGENT a
JOIN PERSONNEL p ON a.handler_id = p.personnel_id
WHERE a.clearance_level > p.clearance_level;

-- =========================================================================
-- Q20.  For each nemesis, show the count of inators they have invented and
-- the count of operations OWCA has run against them.
-- =========================================================================
PROMPT === Q20: Nemesis profile — inators invented vs. operations against them ===
SELECT n.villain_name,
       n.nemesis_level,
       (SELECT COUNT(*) FROM INATOR i    WHERE i.nemesis_id = n.nemesis_id) AS inator_count,
       (SELECT COUNT(*) FROM OPERATION o WHERE o.primary_nemesis_id = n.nemesis_id) AS ops_against
FROM NEMESIS n
ORDER BY n.nemesis_level DESC, n.villain_name;
