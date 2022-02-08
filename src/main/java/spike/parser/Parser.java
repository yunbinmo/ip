package spike.parser;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;

import spike.command.AddCommand;
import spike.command.Command;
import spike.command.DeleteCommand;
import spike.command.ExitCommand;
import spike.command.FindCommand;
import spike.command.IncorrectCommand;
import spike.command.ListCommand;
import spike.command.ToggleMarkCommand;
import spike.exception.SpikeException;
import spike.task.Deadline;
import spike.task.Event;
import spike.task.Task;
import spike.task.TaskList;
import spike.task.ToDo;

/**
 * Encapsulates functionalities to parse user command.
 */
public class Parser {
    protected enum CommandName {
        LIST("list"),
        MARK("mark"),
        UNMARK("unmark"),
        DELETE("delete"),
        TODO("todo"),
        DEADLINE("deadline"),
        EVENT("event"),
        FIND("find"),
        BYE("bye");

        private String command;

        CommandName (String command) {
            this.command = command;
        }

        public String getCommand() {
            return this.command;
        }
    }

    /**
     * Parses user input and returns a command.
     *
     * @param inputLine raw user input
     * @param tasks current task list
     * @return a proper command to be executed
     */
    public Command parseCommand(String inputLine, TaskList tasks) {
        // Extract the words
        String[] commandWords = inputLine.split(" ");
        // Get the command name and check validity
        CommandName type = validateCommand(commandWords[0]);
        if (type == null) {
            return new IncorrectCommand("Sorry, I am not programmed to do this yet :(");
        }
        // Start to generate command
        switch (type) {
        case LIST:
            try {
                return parseList(inputLine);
            } catch (SpikeException e) {
                return new IncorrectCommand(e.getMessage());
            }
        case MARK:
            // Fallthrough
        case UNMARK:
            try {
                return parseToggleMark(type, commandWords, tasks);
            } catch (SpikeException e) {
                return new IncorrectCommand(e.getMessage());
            }
        case DELETE:
            try {
                return parseDelete(commandWords, tasks);
            } catch (SpikeException e) {
                return new IncorrectCommand(e.getMessage());
            }
        case TODO:
            // Fallthrough
        case DEADLINE:
        case EVENT:
            try {
                return parseAdd(type, inputLine, commandWords);
            } catch (SpikeException e) {
                return new IncorrectCommand(e.getMessage());
            }
        case FIND:
            try {
                return parseFind(inputLine);
            } catch (SpikeException e) {
                return new IncorrectCommand(e.getMessage());
            }
        case BYE:
            return parseExit();
        default:
            return new IncorrectCommand("Should not reach this line");
        }
    }

    /**
     * Parses the find command.
     *
     * @param inputLine user raw input
     * @return a command object ready to be executed
     * @throws SpikeException if the keyword for find is missing
     */
    private Command parseFind(String inputLine) throws SpikeException {
        if (inputLine.length() <= 5) {
            throw new SpikeException("Kindly enter the keyword for finding task");
        }
        return new FindCommand(inputLine.substring(5));
    }

    /**
     * Parses the list command.
     *
     * @param inputLine user raw input
     * @return a command object ready to be executed
     * @throws SpikeException if the keyword for list by date is missing
     */
    private Command parseList(String inputLine) throws SpikeException {
        if (inputLine.length() >= 5) {
            // User tries to list task by date
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm");
            LocalDateTime ldt = parseDateTime(inputLine.substring(5), dtf);
            if (ldt == null) {
                throw new SpikeException("Kindly enter the date in the format yyyy-MM-dd 0000 to filter by date");
            }
            return new ListCommand(0, ldt);
        } else {
            return new ListCommand();
        }
    }

    /**
     * Parses the add command
     *
     * @param c command name
     * @param command whole command in string
     * @param commandWords command broken down into words
     * @return a command object ready to be executed
     * @throws SpikeException if any parameter is missing
     */
    private Command parseAdd(CommandName c, String command, String[] commandWords) throws SpikeException {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm");
        switch (c) {
        case TODO:
            if (command.length() <= 5) {
                throw new SpikeException("Hmmmm what to do? Think again?");
            }
            ToDo newTD = new ToDo(command.substring(command.indexOf("todo") + 5));
            return new AddCommand(newTD);
        case DEADLINE:
            // Extract description and deadline and pass to constructor
            if (commandWords.length <= 2 || command.indexOf("/by") == -1
                    || commandWords[1].equals("/by") || command.indexOf("/by") + 3 == command.length()) {
                throw new SpikeException("Deadline or task description missing.");
            }
            LocalDateTime deadlineT = parseDateTime(command.substring(command.indexOf("/by") + 4), dtf);
            if (!(deadlineT == null)) {
                Deadline newD = new Deadline(command.substring(command.indexOf("deadline") + 9,
                        command.indexOf("/by") - 1), deadlineT);
                return new AddCommand(newD);
            } else {
                throw new SpikeException("Please enter a valid date in the format yyyy-MM-dd HHmm");
            }
        case EVENT:
            if (commandWords.length <= 2 || command.indexOf("/at") == -1
                    || commandWords[1].equals("/at") || command.indexOf("/at") + 3 == command.length()) {
                throw new SpikeException("Event time or event description missing.");
            }
            LocalDateTime eventT = parseDateTime(command.substring(command.indexOf("/at") + 4), dtf);
            if (!(eventT == null)) {
                Event newE = new Event(command.substring(command.indexOf("event") + 6,
                        command.indexOf("/at") - 1), eventT);
                return new AddCommand(newE);
            } else {
                throw new SpikeException("Please enter a valid date in the format yyyy-MM-dd HHmm");
            }
        default:
            return new IncorrectCommand("Should not reach this line");
        }
    }

    /**
     * Parses delete command.
     *
     * @param commandWords command broken down into words
     * @param tasks current task list
     * @return a command object ready to be executed
     * @throws SpikeException if any parameter is missing
     */
    private Command parseDelete(String[] commandWords, TaskList tasks) throws SpikeException {
        if (commandWords.length != 2 || isInt(commandWords[1]) == -1
                || isInt(commandWords[1]) > tasks.getListSize()) {
            throw new SpikeException("Invalid arguments for deletion. Please check again!");
        }
        Task toDelete = tasks.getTasks().get(Integer.parseInt(commandWords[1]) - 1);
        return new DeleteCommand(toDelete);
    }

    /**
     * Parses mark or unmark command
     *
     * @param c command name
     * @param commandWords command broken down into words
     * @param tasks current task list
     * @return a command object ready to be executed
     * @throws SpikeException if any parameter is missing
     */
    private Command parseToggleMark(CommandName c, String[] commandWords, TaskList tasks) throws SpikeException {
        if (c.getCommand().equals("mark")) {
            if (commandWords.length != 2 || isInt(commandWords[1]) == -1
                    || isInt(commandWords[1]) > tasks.getListSize()) {
                throw new SpikeException("Invalid arguments for marking. Please check again!");
            }
            Task toMark = tasks.getTasks().get(Integer.parseInt(commandWords[1]) - 1);
            return new ToggleMarkCommand(1, toMark);
        } else {
            if (commandWords.length != 2 || isInt(commandWords[1]) == -1
                    || isInt(commandWords[1]) > tasks.getListSize()) {
                throw new SpikeException("Invalid arguments for unmarking. Please check again!");
            }
            Task toUnmark = tasks.getTasks().get(Integer.parseInt(commandWords[1]) - 1);
            return new ToggleMarkCommand(0, toUnmark);
        }
    }

    /**
     * Parses exit command
     *
     * @return a command ready to be executed
     */
    private Command parseExit() {
        return new ExitCommand();
    }


    /**
     * Parses date and time input by user and returns valid LocalDateTime object
     *
     * @return object containing date and time information
     */
    private static LocalDateTime parseDateTime(String s, DateTimeFormatter dtf) {
        try {
            LocalDateTime dateTime = LocalDateTime.parse(s, dtf);
            return dateTime;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Checks whether the input string is integer.
     * If yes, return it, else return -1.
     *
     * @return indicator of whether the input is an integer
     */
    private static int isInt(String str) {
        try {
            int x = Integer.parseInt(str);
            return x; // it is an integer
        } catch (NumberFormatException e) {
            return -1; // not an integer
        }

    }

    /**
     * Checks whether it is a valid command.
     * If valid, return that command enum number, else return null.
     *
     * @return command name if the input is valid
     */
    private CommandName validateCommand(String input) {
        // Validate if this is an existing command, return it if valid
        return Arrays.stream(CommandName.values())
                .filter(c -> c.getCommand().equals(input))
                .findFirst()
                .orElse(null);
    }
}
