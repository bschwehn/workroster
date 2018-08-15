/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.optaplanner.training.workerrostering.domain;

import java.util.Objects;
import java.util.Set;

public class Employee {

    private final String name;
    private final Set<Skill> skillSet;

    private Set<TimeSlot> unavailableTimeSlotSet;

    public Employee(String name, Set<Skill> skillSet) {
        this.name = name;
        this.skillSet = skillSet;
    }

    public String getName() {
        return name;
    }

    public String getInfo() {
        String info = name + " |";
        for (Skill s : getSkillSet()) {
        	info += s + "|";
        }
        return info;
    }
    public Set<Skill> getSkillSet() {
        return skillSet;
    }

    public Set<TimeSlot> getUnavailableTimeSlotSet() {
        return unavailableTimeSlotSet;
    }

    public void setUnavailableTimeSlotSet(Set<TimeSlot> unavailableTimeSlotSet) {
        this.unavailableTimeSlotSet = unavailableTimeSlotSet;
    }

    public Boolean getHasSkill(Skill skill) {
    	if ("any".equals(skill.toString())) return true;
    	return getSkillSet().contains(skill);
    }

    @Override
    public String toString() {
        return name;
    }

}
