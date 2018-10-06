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

package org.optaplanner.training.workerrostering.persistence;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.drools.compiler.lang.DRL5Expressions.annotationValue_return;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.impl.score.director.ScoreDirector;
import org.optaplanner.core.api.score.constraint.*;
import org.optaplanner.persistence.common.api.domain.solution.SolutionFileIO;
import org.optaplanner.training.workerrostering.domain.Employee;
import org.optaplanner.training.workerrostering.domain.Roster;
import org.optaplanner.training.workerrostering.domain.RosterParametrization;
import org.optaplanner.training.workerrostering.domain.ShiftAssignment;
import org.optaplanner.training.workerrostering.domain.Skill;
import org.optaplanner.training.workerrostering.domain.Spot;
import org.optaplanner.training.workerrostering.domain.TimeSlot;
import org.optaplanner.training.workerrostering.domain.TimeSlotState;

public class WorkerRosteringSolutionFileDaysIO {

	public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm",
			Locale.ENGLISH);

	public static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("EEE dd-MMM", Locale.ENGLISH);

	public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

	private static final IndexedColors NON_EXISTING_COLOR = IndexedColors.GREY_80_PERCENT;
	private static final String NON_EXISTING_COLOR_STRING = "FF333333";

	private static final IndexedColors LOCKED_BY_USER_COLOR = IndexedColors.VIOLET;
	private static final String LOCKED_BY_USER_COLOR_STRING = "FFFF00CC";

	private static final IndexedColors UNAVAILABLE_COLOR = IndexedColors.BLUE_GREY;
	private static final String UNAVAILABLE_COLOR_STRING = "FF6666FF";
	private static final String UNAVAILABLE_COLOR_STRING2 = "FF0070C0";
	private static final String UNAVAILABLE_COLOR_STRING3 = "FF4472C4";
	private static final String UNAVAILABLE_COLOR_STRING4 = "FF666699";

	public static LocalDate startDate = LocalDate.of(2019, 1, 5);
	public static LocalDate endDate = LocalDate.of(2020, 1, 4);

	private static final IndexedColors UNDESIRABLE_COLOR = IndexedColors.GREY_25_PERCENT;
	private static final String UNDESIRABLE_COLOR_STRING = "FFAAAAAA";

	public String getInputFileExtension() {
		return "xlsx";
	}

	public String getOutputFileExtension() {
		return "xlsx";
	}

	public Roster read(File inputSolutionFile) {
		Workbook workbook;
		try (InputStream in = new BufferedInputStream(new FileInputStream(inputSolutionFile))) {
			workbook = new XSSFWorkbook(in);
			return new RosterReader(workbook).readRoster();
		} catch (IOException | RuntimeException e) {
			throw new IllegalStateException(
					"Failed reading inputSolutionFile (" + inputSolutionFile + ") to create a roster.", e);
		}
	}

	private static class RosterReader {

		private final Workbook workbook;

		public RosterReader(Workbook workbook) {
			this.workbook = workbook;
		}

		public Roster readRoster() {
			RosterParametrization rosterParametrization = new RosterParametrization();
			List<Skill> skillList = readListSheet("Skills", new String[] { "Name" }, (Row row) -> {
				String name = row.getCell(0).getStringCellValue();
				return new Skill(name);
			});
			// skillList.add(new Skill("any"));

			Map<String, Skill> skillMap = skillList.stream().collect(Collectors.toMap(Skill::getName, skill -> skill));

			List<Spot> spotList = readListSheet("Spots",
					new String[] { "Name", "Required skill", "Unsuitable Skill", "Days" }, (Row row) -> {
						String name = row.getCell(0).getStringCellValue();
						String requiredSkillName = row.getCell(1).getStringCellValue();
						Skill requiredSkill = skillMap.get(requiredSkillName);
						if (requiredSkill == null) {
							throw new IllegalStateException("The requiredSkillName (" + requiredSkillName
									+ ") does not exist in the skillList (" + skillList + ").");
						}
						Skill unsuitableSkill = null;
						Cell unCell = row.getCell(2);
						if (unCell != null) {
							String unsuitableSkillName = unCell.getStringCellValue();
							unsuitableSkill = skillMap.get(unsuitableSkillName);
						}

						Double days = 8.0;
						try {
							Cell cell = row.getCell(3);
							if (cell != null) {
								days = cell.getNumericCellValue();
							}
						} catch (IllegalStateException e) {
							System.out.println("ERROR: Expected number cell for time in employee " + name);
						}
						Double scoreBeforeVacation = 0.0;
						try {
							Cell cell = row.getCell(4);
							if (cell != null) {
								scoreBeforeVacation = cell.getNumericCellValue();
							}
						} catch (IllegalStateException e) {
							System.out.println("ERROR: Expected number cell for time in employee " + name);
						}
						Double scoreAfterVacation = 0.0;
						try {
							Cell cell = row.getCell(5);
							if (cell != null) {
								scoreAfterVacation = cell.getNumericCellValue();
							}
						} catch (IllegalStateException e) {
							System.out.println("ERROR: Expected number cell for time in employee " + name);
						}
						Double offset = 8.0;
						try {
							Cell cell = row.getCell(6);
							if (cell != null) {
								offset = cell.getNumericCellValue();
							}
						} catch (IllegalStateException e) {
							System.out.println("ERROR: Expected number cell for time in employee " + name);
						}
						return new Spot(name, requiredSkill, unsuitableSkill, days.intValue(),
								scoreBeforeVacation.intValue(), scoreAfterVacation.intValue(), offset.intValue());
					});
			Map<String, Spot> spotMap = spotList.stream().collect(Collectors.toMap(Spot::getName, spot -> spot));
			List<TimeSlot> timeSlotList = generateTimeSlotList();
			List<Employee> employeeList = readListSheet("Employees",
					new String[] { "Name", "Skills", "Night Shift", "Time", "VIP", "Unskills" }, (Row row) -> {
						String name = row.getCell(0).getStringCellValue();
						Set<Skill> skillSet = Arrays.stream(row.getCell(1).getStringCellValue().split(","))
								.map((skillName) -> {
									Skill skill = skillMap.get(skillName);
									if (skill == null) {
										throw new IllegalStateException("The skillName (" + skillName
												+ ") does not exist in the skillList (" + skillList + ").");
									}
									return skill;
								}).collect(Collectors.toSet());
						String nightShift = row.getCell(2).getStringCellValue();
						if (!"n".equals(nightShift)) {
							skillSet.add(skillMap.get("Night"));
						}
						Object f = row.getCell(5);
						if (f != null) {
							String unskillCellValue = row.getCell(5).getStringCellValue();
							if (unskillCellValue != null && unskillCellValue.length() > 0) {
								Set<Skill> unSkillSet = Arrays.stream(row.getCell(5).getStringCellValue().split(","))
										.map((skillName) -> {
											Skill skill = skillMap.get(skillName);
											if (skill == null) {
												throw new IllegalStateException("The skillName (" + skillName
														+ ") does not exist in the skillList (" + skillList + ").");
											}
											return skill;
										}).collect(Collectors.toSet());
								skillSet.addAll(unSkillSet);
							}
						}

						Double time = 100.0;
						try {
							Cell cell = row.getCell(3);
							if (cell != null) {
								time = cell.getNumericCellValue();
							}
						} catch (IllegalStateException e) {
							System.out.println("ERROR: Expected number cell for time in employee " + name);
						}

						Double vipFactor = 0.0;
						try {
							Cell cell = row.getCell(4);
							if (cell != null) {
								vipFactor = cell.getNumericCellValue();
							}
						} catch (IllegalStateException e) {
							System.out.println("ERROR: Expected number cell for VIP in employee " + name);
						}
						Employee employee = new Employee(name, skillSet, time, vipFactor);
						return employee;
					});
			Map<String, Employee> employeeMap = employeeList.stream()
					.collect(Collectors.toMap(Employee::getName, employee -> employee));
			List<ShiftAssignment> shiftAssignmentList = generateShiftAssignmentYear(timeSlotList, spotList);

			/*readGridSheet("Vacation", new String[] { "Name" }, (Row row) -> {
				Cell cell = row.getCell(0);
				if (cell == null) {
					return null;
				}
				String employeeName = cell.getStringCellValue();
				employeeName = trimEmployeeName(employeeName);
				Employee employee = employeeMap.get(employeeName);
				if (employee == null) {
					System.out.println("The employeeName (" + employeeName + ") does not exist in the employeeList ("
							+ employeeList + ").");
					// throw new IllegalStateException("The employeeName (" + employeeName
					// + ") does not exist in the employeeList (" + employeeList + ").");
				}
				return employee;
			}, timeSlotList, (Pair<Employee, TimeSlot> pair, Cell cell) -> {
				if (cell == null)
					return null;
				if (hasStyle(cell, UNAVAILABLE_COLOR)) {
					Employee employee = pair.getKey();
					TimeSlot timeSlot = pair.getValue();
					if (employee.getUndesirableTimeSlotSet().contains(timeSlot)) {
						employee.getUndesirableTimeSlotSet().remove(timeSlot);
					}
					employee.getUnavailableTimeSlotSet().add(timeSlot);
					employee.getUndesirableTimeSlotSet().add(getNextTimeSlot(timeSlotList, timeSlot));
					TimeSlot previousTimeSlot = getPreviousTimeSlot(timeSlotList, timeSlot);
					if (!employee.getUnavailableTimeSlotSet().contains(previousTimeSlot)) {
						employee.getBeforeVacationTimeSlotSet().add(previousTimeSlot);
					}
				}
				return null;
			});
			*/
			readDayGridSheet("Vacation Calendar", new String[] { "Name" }, (Row row) -> {
				Cell cell = row.getCell(0);
				if (cell == null) {
					return null;
				}
				String employeeName = trimEmployeeName(cell.getStringCellValue());
				Employee employee = employeeMap.get(employeeName);
				if (employee == null) {
					System.out.println("The employeeName (" + employeeName + ") does not exist in the employeeList ("
							+ employeeList + ").");
					// throw new IllegalStateException("The employeeName (" + employeeName
					// + ") does not exist in the employeeList (" + employeeList + ").");
				}
				return employee;
			}, timeSlotList, (Pair<Employee, LocalDate> pair, Cell cell) -> {
				if (cell == null)
					return null;
				if (hasStyle(cell, UNAVAILABLE_COLOR)) {
					Employee employee = pair.getKey();
					LocalDate date = pair.getValue();
					if (employee.getUndesirableDateSet().contains(date)) {
						employee.getUndesirableDateSet().remove(date);
					}
					employee.getUnavailableDateSet().add(date);
					employee.getUndesirableDateSet().add(date.plusDays(1));
					LocalDate previousDay = date.plusDays(-1);
				}
				return null;
			});
			
			calculateVacationTimeSlotsFromDates(timeSlotList, employeeList);
			calculateBeforeAfterVacationTimeSlots(timeSlotList, employeeList);
			return new Roster(rosterParametrization, skillList, spotList, timeSlotList, employeeList,
					shiftAssignmentList);
		}

		private void calculateBeforeAfterVacationTimeSlots(List<TimeSlot> timeSlots, List<Employee> employeeList) {
			// if vacation includes Monday, previous timeslot (S-F) is before vacation
			for (Employee employee : employeeList) {
				Set<LocalDate> vacationDays = employee.getUnavailableDateSet();
				Set<LocalDate> undesirableDays = employee.getUndesirableDateSet();
				Set<TimeSlot> vacationSlots = employee.getUnavailableTimeSlotSet();
				Set<TimeSlot> beforeVacationSlots = employee.getBeforeVacationTimeSlotSet();
				Set<TimeSlot> afterVacationSlots = employee.getAfterVacationTimeSlotSet();

				for (LocalDate vacationDay : vacationDays) {
					if (vacationDay.getDayOfWeek() != DayOfWeek.MONDAY) continue;
					// get previous timeslot (ending previous friday)
					LocalDate previousFriday = vacationDay.plusDays(-3);

					assert(previousFriday.getDayOfWeek() == DayOfWeek.FRIDAY);

					if (vacationDays.contains(previousFriday)) continue;

					for (TimeSlot slot : timeSlots) {
						LocalDate end = slot.getEndDateTime().toLocalDate();
						if (end.isEqual(previousFriday)) {
							beforeVacationSlots.add(slot);
						}
					}
					
					// set weekend before as undesirable
					undesirableDays.add(previousFriday.plusDays(1));
					undesirableDays.add(previousFriday.plusDays(2));
				}

				for (LocalDate vacationDay : vacationDays) {
					if (vacationDay.getDayOfWeek() != DayOfWeek.FRIDAY) continue;
					// get next timeslot (starting next saturday)
					LocalDate nextSaturday = vacationDay.plusDays(1);

					assert(nextSaturday.getDayOfWeek() == DayOfWeek.SATURDAY);

					if (vacationDays.contains(nextSaturday)) continue;

					for (TimeSlot slot : timeSlots) {
						LocalDate start = slot.getStartDateTime().toLocalDate();
						if (start.isEqual(nextSaturday)) {
							afterVacationSlots.add(slot);
						}
					}

					// set weekend after as undesirable
					undesirableDays.add(nextSaturday);
					undesirableDays.add(nextSaturday.plusDays(1));
				}
			}
		}

		private void calculateVacationTimeSlotsFromDates(List<TimeSlot> timeSlots, List<Employee> employeeList) {
			// to reuse some of the week based calculations, calculate week based vacation timeslots from
			// dates
			
			// vacation day in saturday to friday period => set timeslot as vacation
			for (Employee employee : employeeList) {
				Set<LocalDate> vacationDays = employee.getUnavailableDateSet();
				Set<TimeSlot> vacationSlots = employee.getUnavailableTimeSlotSet();

				for (TimeSlot slot : timeSlots) {
					LocalDate start = slot.getStartDateTime().toLocalDate();
					LocalDate end = slot.getEndDateTime().toLocalDate();
					for (LocalDate date = start; date.isBefore(end); date = date.plusDays(1)) {
						if (vacationDays.contains(date) && !vacationSlots.contains(slot)) {
							vacationSlots.add(slot);
						}
					}
				}
			}
		}

		private String trimEmployeeName(String employeeName) {
			return employeeName.split(" ")[0];

		}

		private TimeSlot getNextTimeSlot(List<TimeSlot> slots, TimeSlot slot) {
			for (int i = 0; i < slots.size() - 1; ++i) {
				if (slots.get(i) == slot) {
					return slots.get(i + 1);
				}
			}
			return null;
		}

		private TimeSlot getPreviousTimeSlot(List<TimeSlot> slots, TimeSlot slot) {
			for (int i = 1; i < slots.size(); ++i) {
				if (slots.get(i) == slot) {
					return slots.get(i - 1);
				}
			}
			return null;
		}

		private List<TimeSlot> generateTimeSlotList() {
			List<TimeSlot> slots = new ArrayList<TimeSlot>();
			LocalDateTime startDate = LocalDateTime.of(2019, 1, 5, 0, 0);
			while (startDate.getYear() < 2020) {

				LocalDateTime endDate = startDate.plusWeeks(1);
				TimeSlot slot = new TimeSlot(startDate, endDate);
				slot.setTimeSlotState(TimeSlotState.DRAFT);
				slots.add(slot);
				startDate = endDate;
			}

			return slots;
		}

		private List<ShiftAssignment> generateShiftAssignmentYear(List<TimeSlot> timeSlotList, List<Spot> spotList) {
			List<ShiftAssignment> shiftAssignments = new ArrayList<ShiftAssignment>();

			for (TimeSlot slot : timeSlotList) {
				for (Spot spot : spotList) {

					ShiftAssignment sa = new ShiftAssignment(spot, slot);
					shiftAssignments.add(sa);
				}
			}
			return shiftAssignments;
		}

		private <E> List<E> readListSheet(String sheetName, String[] headerTitles, Function<Row, E> rowMapper) {

			return readListSheet(sheetName, headerTitles, rowMapper, 2);
		}

		private <E> List<E> readListSheet(String sheetName, String[] headerTitles, Function<Row, E> rowMapper,
				int numHeaderRows) {
			int firstDataRowIndex = numHeaderRows;
			Sheet sheet = workbook.getSheet(sheetName);
			if (sheet == null) {
				throw new IllegalStateException("The workbook does not contain a sheet with name (" + sheetName + ").");
			}
			Row headerRow = sheet.getRow(numHeaderRows - 1);
			if (headerRow == null) {
				throw new IllegalStateException("The sheet (" + sheetName + ") has no header data at row (1).");
			}
			int columnNumber = 0;
			for (String headerTitle : headerTitles) {
				Cell cell = headerRow.getCell(columnNumber);
				if (cell == null) {
					throw new IllegalStateException("The sheet (" + sheetName + ") at header cell ("
							+ cell.getRowIndex() + "," + cell.getColumnIndex() + ") does not contain the headerTitle ("
							+ headerTitle + ").");
				}
				if (!cell.getStringCellValue().equals(headerTitle)) {
					throw new IllegalStateException("The sheet (" + sheetName + ") at header cell ("
							+ cell.getRowIndex() + "," + cell.getColumnIndex() + ") does not contain the headerTitle ("
							+ headerTitle + "), it contains cellValue (" + cell.getStringCellValue() + ") instead.");
				}
				columnNumber++;
			}
			List<E> elementList = new ArrayList<>(sheet.getLastRowNum() - firstDataRowIndex);
			for (int i = firstDataRowIndex; i <= sheet.getLastRowNum(); i++) {
				Row row = sheet.getRow(i);
				if (row == null) {
					continue;
				}
				Cell firstCell = row.getCell(0);
				if (firstCell == null) {
					break;
				}
				for (int j = 0; j < headerTitles.length; j++) {
					Cell cell = row.getCell(j);
					if (cell == null) {
						System.out.println("The sheet (" + sheetName + ") has no cell for " + headerTitles[j]
								+ " at row (" + i + ") at column (" + j + ").");
					}
				}
				E element = rowMapper.apply(row);
				if (element != null) {
					elementList.add(element);
				}
			}
			return elementList;
		}

		private <E, F> List<F> readDayGridSheet(String sheetName, String[] headerTitles, Function<Row, E> rowMapper,
				List<TimeSlot> timeSlotList, BiFunction<Pair<E, LocalDate>, Cell, F> cellMapper) {
			Sheet sheet = workbook.getSheet(sheetName);
			int numHeaderRows = 0;
			Row weekHeaderRow = sheet.getRow(numHeaderRows++);
			Row monthHeaderRow = sheet.getRow(numHeaderRows++);
			Row dayHeaderRow = sheet.getRow(numHeaderRows++);
			Row weekDayHeaderRow = sheet.getRow(numHeaderRows++);

			readListSheet(sheetName, headerTitles, rowMapper, numHeaderRows);

			List<F> cellElementList = new ArrayList<>((sheet.getLastRowNum() - numHeaderRows) * timeSlotList.size());
			for (int i = numHeaderRows; i <= sheet.getLastRowNum(); i++) {
				int columnNumber = headerTitles.length;
				Row row = sheet.getRow(i);
				if (row == null) {
					continue;
				}
				E rowElement = rowMapper.apply(row);
				for (LocalDate date = startDate; date.isBefore(endDate); date = date.plusDays(1)) {
					Cell cell = row.getCell(columnNumber);

					F cellElement = cellMapper.apply(Pair.of(rowElement, date), cell);
					if (cellElement != null) {
						cellElementList.add(cellElement);
					}
					columnNumber++;
				}
			}
			return cellElementList;
		}

		private <E, F> List<F> readGridSheet(String sheetName, String[] headerTitles, Function<Row, E> rowMapper,
				List<TimeSlot> timeSlotList, BiFunction<Pair<E, TimeSlot>, Cell, F> cellMapper) {
			readListSheet(sheetName, headerTitles, rowMapper);
			Sheet sheet = workbook.getSheet(sheetName);
			Row higherHeaderRow = sheet.getRow(0);
			Row lowerHeaderRow = sheet.getRow(1);
			int columnNumber = headerTitles.length;
			for (TimeSlot timeSlot : timeSlotList) {
				if (timeSlot.getStartDateTime().getHour() == 6) {
					String expectedDayString = timeSlot.getStartDateTime().toLocalDate().format(DAY_FORMATTER);
					Cell higherCell = higherHeaderRow.getCell(columnNumber);
					if (higherCell == null) {
						throw new IllegalStateException("The sheet (" + sheetName + ") at header cell ("
								+ higherCell.getRowIndex() + "," + higherCell.getColumnIndex()
								+ ") does not contain the date (" + expectedDayString + ").");
					}
					if (!higherCell.getStringCellValue().equals(expectedDayString)) {
						throw new IllegalStateException("The sheet (" + sheetName + ") at header cell ("
								+ higherCell.getRowIndex() + "," + higherCell.getColumnIndex()
								+ ") does not contain the date (" + expectedDayString + "), it contains cellValue ("
								+ higherCell.getStringCellValue() + ") instead.");
					}
				}
				String expectedStartDateTimeString = timeSlot.getSlotName();
				Cell lowerCell = lowerHeaderRow.getCell(columnNumber);
				if (lowerCell == null) {
					throw new IllegalStateException("The sheet (" + sheetName + ") at header cell ("
							+ lowerCell.getRowIndex() + "," + lowerCell.getColumnIndex()
							+ ") does not contain the startDateTime (" + expectedStartDateTimeString + ").");
				}
				if (!lowerCell.getStringCellValue().equals(expectedStartDateTimeString)) {
					throw new IllegalStateException("The sheet (" + sheetName + ") at header cell ("
							+ lowerCell.getRowIndex() + "," + lowerCell.getColumnIndex()
							+ ") does not contain the startDateTime (" + expectedStartDateTimeString
							+ "), it contains cellValue (" + lowerCell.getStringCellValue() + ") instead.");
				}
				columnNumber++;
			}

			List<F> cellElementList = new ArrayList<>((sheet.getLastRowNum() - 2) * timeSlotList.size());
			for (int i = 2; i <= sheet.getLastRowNum(); i++) {
				Row row = sheet.getRow(i);
				if (row == null) {
					continue;
				}
				E rowElement = rowMapper.apply(row);
				for (int j = 0; j < timeSlotList.size(); j++) {
					TimeSlot timeSlot = timeSlotList.get(j);
					Cell cell = row.getCell(headerTitles.length + j);

					F cellElement = cellMapper.apply(Pair.of(rowElement, timeSlot), cell);
					if (cellElement != null) {
						cellElementList.add(cellElement);
					}
				}
			}
			return cellElementList;
		}

		private boolean hasStyle(Cell cell, IndexedColors color) {
			Boolean isIndexedMatch = cell.getCellStyle().getFillForegroundColor() == color.getIndex()
					&& cell.getCellStyle().getFillPattern() == CellStyle.SOLID_FOREGROUND;
			if (isIndexedMatch)
				return true;

			if (cell.getCellStyle().getFillBackgroundColor() == 0
					|| cell.getCellStyle().getFillBackgroundColor() == 64) {
				org.apache.poi.xssf.usermodel.XSSFColor col = (org.apache.poi.xssf.usermodel.XSSFColor) cell
						.getCellStyle().getFillForegroundColorColor();
				if (col != null) {
					if (color == NON_EXISTING_COLOR) {
						return Objects.equals(col.getARGBHex(), NON_EXISTING_COLOR_STRING);
					}
					if (color == LOCKED_BY_USER_COLOR) {
						return Objects.equals(col.getARGBHex(), LOCKED_BY_USER_COLOR_STRING);
					}
					if (color == UNAVAILABLE_COLOR) {
						return Objects.equals(col.getARGBHex(), UNAVAILABLE_COLOR_STRING)
								|| Objects.equals(col.getARGBHex(), UNAVAILABLE_COLOR_STRING2)
								|| Objects.equals(col.getARGBHex(), UNAVAILABLE_COLOR_STRING3)
								|| Objects.equals(col.getARGBHex(), UNAVAILABLE_COLOR_STRING4);
					}
				}
			}
			return false;
		}
	}

	public void write(Roster roster, Solver<Roster> solver, File outputSolutionFile) {
		Workbook workbook = new RosterWriter(roster, solver).writeWorkbook();
		try (FileOutputStream out = new FileOutputStream(outputSolutionFile)) {
			workbook.write(out);
		} catch (IOException e) {
			throw new IllegalStateException(
					"Failed writing outputSolutionFile (" + outputSolutionFile + ") for roster (" + roster + ").", e);
		}
	}

	private static class RosterWriter {

		private final Roster roster;
		private final Solver<Roster> solver;

		private final Workbook workbook;
		private final CellStyle headerStyle;

		private final CellStyle nonExistingStyle;
		private final CellStyle lockedByUserStyle;
		private final CellStyle unavailableStyle;
		private final CellStyle undesirableStyle;

		public RosterWriter(Roster roster, Solver<Roster> solver) {
			this.roster = roster;
			this.solver = solver;
			workbook = new XSSFWorkbook();
			headerStyle = workbook.createCellStyle();
			Font font = workbook.createFont();
			font.setBold(true);
			headerStyle.setFont(font);
			nonExistingStyle = createStyle(NON_EXISTING_COLOR);
			lockedByUserStyle = createStyle(LOCKED_BY_USER_COLOR);
			unavailableStyle = createStyle(UNAVAILABLE_COLOR);
			undesirableStyle = createStyle(UNDESIRABLE_COLOR);
		}

		public Workbook writeWorkbook() {
			Map<Pair<Spot, TimeSlot>, ShiftAssignment> spotMap = roster.getShiftAssignmentList().stream()
					.collect(Collectors.toMap(
							shiftAssignment -> Pair.of(shiftAssignment.getSpot(), shiftAssignment.getTimeSlot()),
							shiftAssignment -> shiftAssignment));
			Map<Pair<Employee, TimeSlot>, List<ShiftAssignment>> employeeMap = roster.getShiftAssignmentList().stream()
					.collect(Collectors.groupingBy(
							shiftAssignment -> Pair.of(shiftAssignment.getEmployee(), shiftAssignment.getTimeSlot()),
							Collectors.toList()));

			writeGridSheet("Spot roster", new String[] { "Name" }, roster.getSpotList(), (Row row, Spot spot) -> {
				row.createCell(0).setCellValue(spot.getName());
			}, (Cell cell, Pair<Spot, TimeSlot> pair) -> {
				ShiftAssignment shiftAssignment = spotMap.get(pair);
				if (shiftAssignment == null) {
					cell.setCellStyle(nonExistingStyle);
					cell.setCellValue(" "); // TODO HACK to get a clearer xlsx file
					return;
				}
				if (shiftAssignment.isLockedByUser()) {
					cell.setCellStyle(lockedByUserStyle);
				}
				Employee employee = shiftAssignment.getEmployee();
				if (employee == null) {
					cell.setCellValue("?");
					return;
				}
				cell.setCellValue(employee.getName());
			});
			writeGridSheet("Employee roster", new String[] { "Name" }, roster.getEmployeeList(),
					(Row row, Employee employee) -> {
						row.createCell(0).setCellValue(employee.getInfo());
					}, (Cell cell, Pair<Employee, TimeSlot> pair) -> {
						Employee employee = pair.getKey();
						TimeSlot timeSlot = pair.getValue();
						if (employee.getUnavailableTimeSlotSet().contains(timeSlot)) {
							cell.setCellStyle(unavailableStyle);
						}
						if (employee.getUndesirableTimeSlotSet().contains(timeSlot)) {
							cell.setCellStyle(undesirableStyle);
						}
						List<ShiftAssignment> shiftAssignmentList = employeeMap.get(pair);
						if (shiftAssignmentList == null) {
							cell.setCellValue(" "); // TODO HACK to get a clearer xlsx file
							return;
						}
						cell.setCellValue(shiftAssignmentList.stream()
								.map((shiftAssignment) -> shiftAssignment.getSpot().getName())
								.collect(Collectors.joining(",")));
					});
			writeCalendar("Calendar", new String[] { "Name" }, false, roster.getEmployeeList(),
					(Row row, Employee employee) -> {
						row.createCell(0).setCellValue(employee.getInfo());
					});
			writeCalendar("Vacation Calendar", new String[] { "Name" }, true, roster.getEmployeeList(),
					(Row row, Employee employee) -> {
						row.createCell(0).setCellValue(employee.getInfo());
					});
			writeListSheet("Employees", new String[] { "Name", "Skills" }, roster.getEmployeeList(),
					(Row row, Employee employee) -> {
						row.createCell(0).setCellValue(employee.getName());
						row.createCell(1).setCellValue(
								employee.getSkillSet().stream().map(Skill::getName).collect(Collectors.joining(",")));
					});
			writeListSheet("Timeslots", new String[] { "Start", "End", "State" }, roster.getTimeSlotList(),
					(Row row, TimeSlot timeSlot) -> {
						row.createCell(0).setCellValue(timeSlot.getStartDateTime().format(DATE_TIME_FORMATTER));
						row.createCell(1).setCellValue(timeSlot.getEndDateTime().format(DATE_TIME_FORMATTER));
						row.createCell(2).setCellValue(timeSlot.getTimeSlotState().name());
					});
			writeListSheet("Spots", new String[] { "Name", "Required skill", "Unsuitable Skill" }, roster.getSpotList(),
					(Row row, Spot spot) -> {
						row.createCell(0).setCellValue(spot.getName());
						Skill requiredSkill = spot.getRequiredSkill();
						if (requiredSkill != null) {
							row.createCell(1).setCellValue(spot.getRequiredSkill().getName());
						}
						Skill unsuitableSkill = spot.getUnsuitableSkill();
						if (unsuitableSkill != null) {
							row.createCell(2).setCellValue(spot.getUnsuitableSkill().getName());
						}
						int days = spot.getDays();
						row.createCell(3).setCellValue(days);
						int score = spot.getScoreBeforeVacation();
						row.createCell(4).setCellValue(score);
						int offset = spot.getOffset();
						row.createCell(5).setCellValue(offset);
					});
			writeListSheet("Skills", new String[] { "Name" }, roster.getSkillList(), (Row row, Skill skill) -> {
				row.createCell(0).setCellValue(skill.getName());
			});

			writeSkillCalendars();
			List<String> summaryHeader = new ArrayList<String>();
			List<String> shiftTypes = new ArrayList<String>();

			summaryHeader.add("Name");
			summaryHeader.add("Total Shifts");
			summaryHeader.add("Total Days");
			summaryHeader.add("Total Cost");
			summaryHeader.add("Normalized Total Hours");
			for (Spot spot : roster.getSpotList()) {
				summaryHeader.add(spot.getName());
				if (!shiftTypes.contains(spot.getShiftType())) {
					shiftTypes.add(spot.getShiftType());
				}
			}
			for (String shiftType : shiftTypes) {
				summaryHeader.add(shiftType);
			}

			writeListSheet("Summary", summaryHeader.toArray(new String[summaryHeader.size()]), roster.getEmployeeList(),
					(Row row, Employee emp) -> {
						row.createCell(0).setCellValue(emp.getInfo());
						List<ShiftAssignment> er = roster.getEmployeeAssignments(emp);

						int totalShifts = er.size();
						double totalDays = er.stream().mapToDouble(s -> s.getSpot().getDays()).sum();
						double totalCost = er.stream().mapToDouble(s -> s.getCost()).sum();
						double normalizedHours = totalDays * 100.0 / emp.getTime();

						int cell = 0;
						row.createCell(++cell).setCellValue(totalShifts);
						row.createCell(++cell).setCellValue(totalDays);
						row.createCell(++cell).setCellValue(totalCost);
						row.createCell(++cell).setCellValue(normalizedHours);
						for (Spot spot : roster.getSpotList()) {
							long spotCnt = er.stream().filter(a -> a.getSpot() == spot).count();

							long spotCost = er.stream().filter(a -> a.getSpot() == spot)
									.mapToLong(a -> a.getAdjustedCost()).sum();
							String val = spotCnt + " (" + spotCost + ")";
							row.createCell(++cell).setCellValue(val);
						}
						for (String shiftType : shiftTypes) {
							long typeCnt = er.stream().filter(a -> a.getSpot().getShiftType().equals(shiftType))
									.count();
							long typeCost = er.stream().filter(a -> a.getSpot().getShiftType().equals(shiftType))
									.mapToLong(a -> a.getAdjustedCost()).sum();
							String val = typeCnt + " (" + typeCost + ")";
							row.createCell(++cell).setCellValue(val);
						}
					});

			ScoreDirector<Roster> score = solver.getScoreDirectorFactory().buildScoreDirector();
			score.setWorkingSolution(solver.getBestSolution());
			Collection<ConstraintMatchTotal> matchTotals = score.getConstraintMatchTotals();
			Map<Object, Indictment> indictment = score.getIndictmentMap();

			writeListSheet("Score Summary", new String[] { "Rule", "Level", "Score" }, matchTotals,
					(Row row, ConstraintMatchTotal match) -> {
						row.createCell(0).setCellValue(match.getConstraintName());
						row.createCell(1).setCellValue(match.getScore().toString());
						// row.createCell(1).setCellValue( match.getWeightTotalAsNumber().toString());
					});

			writeListSheet("Score Details", new String[] { "Employee", "Rule", "Score Lvl 1" },
					roster.getEmployeeList(), (Row row, Employee emp) -> {

						int cell = 0;
						row.createCell(cell++).setCellValue(emp.getInfo());
						if (indictment.containsKey(emp)) {
							Indictment matches = indictment.get(emp);
							for (ConstraintMatch match : matches.getConstraintMatchSet()) {
								Number[] levelScores = match.getScore().toLevelNumbers();
								row.createCell(cell++).setCellValue(match.getConstraintName());
								for (Number sc : levelScores) {
									row.createCell(cell++).setCellValue(sc.toString());
								}
							}
						}
					});
			return workbook;
		}

		private void writeSkillCalendars() {
			for (Skill skill : roster.getSkillList()) {
				List<Employee> skilledEmployees = roster.getEmployeeList().stream().filter(e -> e.getHasSkill(skill)).collect(Collectors.toList());
				String sheetName = "Cal_" + skill.getName();

				writeCalendar(sheetName, new String[] { "Name" }, false, skilledEmployees,
					(Row row, Employee employee) -> {
						row.createCell(0).setCellValue(employee.getInfo());
					});
			
			}
		}

		private <E> Sheet writeListSheet(String sheetName, String[] headerTitles, Collection<E> elementList,
				BiConsumer<Row, E> rowConsumer) {
			return writeListSheet(sheetName, headerTitles, elementList, rowConsumer, 1);
		}

		private <E> Sheet writeListSheet(String sheetName, String[] headerTitles, Collection<E> elementList,
				BiConsumer<Row, E> rowConsumer, int startRow) {
			Sheet sheet = workbook.createSheet(sheetName);
			sheet.setDefaultColumnWidth(20);
			int rowNumber = 0;
			do {
				sheet.createRow(rowNumber++); // Leave empty
			} while (rowNumber < startRow);

			Row headerRow = sheet.createRow(rowNumber++);
			int columnNumber = 0;
			for (String headerTitle : headerTitles) {
				Cell cell = headerRow.createCell(columnNumber);
				cell.setCellValue(headerTitle);
				cell.setCellStyle(headerStyle);
				columnNumber++;
			}
			sheet.createFreezePane(1, 2);
			for (E element : elementList) {
				Row row = sheet.createRow(rowNumber);
				rowConsumer.accept(row, element);
				rowNumber++;
			}
			return sheet;
		}

		private <E> void writeGridSheet(String sheetName, String[] headerTitles, List<E> rowElementList,
				BiConsumer<Row, E> rowConsumer, BiConsumer<Cell, Pair<E, TimeSlot>> cellConsumer) {
			Sheet sheet = writeListSheet(sheetName, headerTitles, rowElementList, rowConsumer);
			sheet.setDefaultColumnWidth(5);
			sheet.createFreezePane(headerTitles.length, 2);
			Row higherHeaderRow = sheet.getRow(0);
			Row lowerHeaderRow = sheet.getRow(1);
			int columnNumber = headerTitles.length;
			for (TimeSlot timeSlot : roster.getTimeSlotList()) {
				if (timeSlot.getStartDateTime().getHour() == 6) {
					Cell cell = higherHeaderRow.createCell(columnNumber);
					cell.setCellValue(timeSlot.getStartDateTime().toLocalDate().format(DAY_FORMATTER));
					cell.setCellStyle(headerStyle);
					sheet.addMergedRegion(new CellRangeAddress(0, 0, columnNumber, columnNumber + 2));
				}
				Cell cell = lowerHeaderRow.createCell(columnNumber);
				cell.setCellValue(timeSlot.getSlotName());
				cell.setCellStyle(headerStyle);
				columnNumber++;
			}
			int rowNumber = 2;
			for (E rowElement : rowElementList) {
				Row row = sheet.getRow(rowNumber);
				columnNumber = headerTitles.length;
				for (TimeSlot timeSlot : roster.getTimeSlotList()) {
					Cell cell = row.createCell(columnNumber);
					cellConsumer.accept(cell, Pair.of(rowElement, timeSlot));
					columnNumber++;
				}
				rowNumber++;
			}
		}

		private void writeCalendar(String sheetName, String[] headerTitles, Boolean vacationOnly,
				List<Employee> employeeList, BiConsumer<Row, Employee> rowConsumer) {

			Sheet sheet = writeListSheet(sheetName, headerTitles, employeeList, rowConsumer, 3);
			sheet.setDefaultColumnWidth(5);
			sheet.createFreezePane(headerTitles.length, 4);
			Row weekHeaderRow = sheet.getRow(0);
			Row monthHeaderRow = sheet.getRow(1);
			Row dayHeaderRow = sheet.getRow(2);
			Row weekdayHeaderRow = sheet.getRow(3);

			weekHeaderRow.createCell(0).setCellValue("KW");
			monthHeaderRow.createCell(0).setCellValue("M");
			dayHeaderRow.createCell(0).setCellValue("T");
			// weekdayHeaderRow.createCell(0).setCellValue("WT");

			int columnNumber = headerTitles.length;
			for (LocalDate date = startDate; date.isBefore(endDate); date = date.plusDays(1)) {
				Cell weekCell = weekHeaderRow.createCell(columnNumber);
				Cell monthCell = monthHeaderRow.createCell(columnNumber);
				Cell dayCell = dayHeaderRow.createCell(columnNumber);
				Cell weekdayCell = weekdayHeaderRow.createCell(columnNumber);

				weekCell.setCellValue(date.get(WeekFields.ISO.weekOfYear()));
				monthCell.setCellValue(date.getMonthValue());
				dayCell.setCellValue(date.getDayOfMonth());
				weekdayCell.setCellValue(date.getDayOfWeek().getDisplayName(TextStyle.NARROW, Locale.GERMAN));

				++columnNumber;
			}

			int rowNumber = 4;
			for (Employee employee : employeeList) {
				Row row = sheet.getRow(rowNumber);
				int startColumnNumber = headerTitles.length;
				if (true || !vacationOnly) {
					List<ShiftAssignment> shifts = roster.getEmployeeAssignments(employee);
					for (ShiftAssignment shift : shifts) {
						for (LocalDate shiftDay : shift.getDays()) {

						int startColumn = getColumnNumberForDate(startColumnNumber,
								shiftDay);
							Cell cell = row.createCell(startColumn);
							cell.setCellValue(shift.getSpot().toString());
						}
					}
				}

				if (true || vacationOnly) {
					Set<LocalDate> dates = employee.getUnavailableDateSet();
					if (dates != null) {
						for (LocalDate date : dates) {
							int startColumn = getColumnNumberForDate(startColumnNumber, date);
							int endColumn = getColumnNumberForDate(startColumnNumber, date);
							for (int i = startColumn; i <= endColumn; ++i) {
								Boolean existingCell = true;
								Cell cell = row.getCell(i);
								if (cell == null) {
									cell = row.createCell(i);
									existingCell = false;
								}
								cell.setCellStyle(unavailableStyle);
								if (!existingCell) {
									cell.setCellValue(" "); // TODO HACK to get a clearer xlsx file
								}
							}
						}
					}

					dates = employee.getUndesirableDateSet();
					if (dates != null) {
						for (LocalDate date : dates) {
							int startColumn = getColumnNumberForDate(startColumnNumber, date);
							int endColumn = getColumnNumberForDate(startColumnNumber, date);
							for (int i = startColumn; i <= endColumn; ++i) {
								Boolean existingCell = true;
								Cell cell = row.getCell(i);
								if (cell == null) {
									cell = row.createCell(i);
									existingCell = false;
								}
								cell.setCellStyle(undesirableStyle);
								if (!existingCell) {
									cell.setCellValue(" "); // TODO HACK to get a clearer xlsx file
								}
							}
						}
					}
				} else {
					Set<TimeSlot> slots = employee.getUnavailableTimeSlotSet();
					for (TimeSlot slot : slots) {
						int startColumn = getColumnNumberForDate(startColumnNumber,
								slot.getStartDateTime().toLocalDate());
						int endColumn = getColumnNumberForDate(startColumnNumber, slot.getEndDateTime().toLocalDate());
						for (int i = startColumn; i < endColumn; ++i) {
							Boolean existingCell = true;
							Cell cell = row.getCell(i);
							if (cell == null) {
								cell = row.createCell(i);
								existingCell = false;
							}
							cell.setCellStyle(unavailableStyle);
							if (!existingCell) {
								cell.setCellValue(" "); // TODO HACK to get a clearer xlsx file
							}
						}
					}
				}

				rowNumber++;
			}
		}

		private int getColumnNumberForDate(int initialColumn, LocalDate date) {
			int diff = (int) ChronoUnit.DAYS.between(startDate, date);
			return diff + initialColumn;
		}

		private CellStyle createStyle(IndexedColors color) {
			CellStyle style = workbook.createCellStyle();
			style.setFillForegroundColor(color.getIndex());
			style.setFillPattern(CellStyle.SOLID_FOREGROUND);
			return style;
		}

	}

}
