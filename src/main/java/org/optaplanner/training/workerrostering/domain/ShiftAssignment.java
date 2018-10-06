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

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.variable.PlanningVariable;
import org.optaplanner.training.workerrostering.optional.domain.MovableShiftAssignmentFilter;

@PlanningEntity(movableEntitySelectionFilter = MovableShiftAssignmentFilter.class)
public class ShiftAssignment implements Serializable {

    private final Spot spot;
    private final TimeSlot timeSlot;
    private Set<LocalDate> days;
    private Roster roster;

    private boolean lockedByUser = false;

    @PlanningVariable(valueRangeProviderRefs = "employeeRange")
    private Employee employee = null;

    private ShiftAssignment() {
        spot = null;
        timeSlot = null;
    }
    
    public void setRoster(Roster roster) {
    	this.roster = roster;
    }

    public ShiftAssignment(Spot spot, TimeSlot timeSlot) {
        this.timeSlot = timeSlot;
        this.spot = spot;
        generateDays();
    }

    private void generateDays() {
    	LocalDate start = this.timeSlot.getStartDateTime().toLocalDate().plusDays(this.spot.getOffset());
    	LocalDate end = start.plusDays(this.spot.getDays());
    	
    	this.days = new LinkedHashSet<>();
		for (LocalDate date = start; date.isBefore(end); date = date.plusDays(1)) {
			days.add(date);
		}
	}

	public int getShiftVacationOverlap() {
        return this.getEmployee().getShiftVacationOverlap(this);
    }

	public int getShiftUndesirableOverlap() {
        return this.getEmployee().getShiftUndesirableOverlap(this);
    }


	public Spot getSpot() {
        return spot;
    }

	public Set<LocalDate> getDays() {
        return days;
    }

    @Deprecated
    public TimeSlot getTimeSlot() {
        return timeSlot;
    }

    public boolean isLockedByUser() {
        return lockedByUser;
    }

    public void setLockedByUser(boolean lockedByUser) {
        this.lockedByUser = lockedByUser;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    @Deprecated
    public LocalDateTime getStartDateTime() {
        return this.getTimeSlot().getStartDateTime().plusDays(this.getSpot().getOffset());
    }
    
    @Deprecated
    public LocalDateTime getEndDateTime() {
        return this.getTimeSlot().getStartDateTime().plusDays(this.getSpot().getOffset() + this.getSpot().getDays());
    }
    @Override
    public String toString() {
        return spot + " " + timeSlot;
    }
    
    public long getCost() {
    	return (long)(spot.getDays() * 10);
    }

    public long getAdjustedCost() {
    	return (long)(spot.getDays() * 100 / employee.getTime());
    }

}
