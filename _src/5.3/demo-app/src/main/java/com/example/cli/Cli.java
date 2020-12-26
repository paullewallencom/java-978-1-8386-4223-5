package com.example.cli;

import com.example.warehouse.*;
import com.example.warehouse.delivery.ReportDelivery;
import com.example.warehouse.delivery.ReportDeliveryException;
import com.example.warehouse.export.ExportType;
import com.example.warehouse.export.Exporter;
import com.example.warehouse.export.util.CopyByteArrayOutputStream;
import com.example.warehouse.plot.ChartPlotter;
import com.example.warehouse.plot.ChartType;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.IntStream;

public class Cli implements Runnable {

    static final class MenuOption {

        int number;
        String label;

        MenuOption(int number, String label) {
            this.number = number;
            this.label = label;
        }
    }

    private static final Scanner SCANNER = new Scanner(System.in);

    private static final List<MenuOption> MAIN_MENU_OPTIONS = List.of(
        new MenuOption(1, "Manage products"),
        new MenuOption(2, "Manage customers"),
        new MenuOption(3, "Manage orders"),
        new MenuOption(4, "Export reports"),
        new MenuOption(5, "Report charts"),
        new MenuOption(6, "Settings"),
        new MenuOption(7, "Exit program")
    );

    private static final List<MenuOption> PRODUCT_OPTIONS = List.of(
        new MenuOption(1, "List products"),
        new MenuOption(2, "Add product"),
        new MenuOption(3, "Update product"),
        new MenuOption(4, "Delete product"),
        new MenuOption(5, "Go back to previous menu")
    );

    private static final List<MenuOption> CUSTOMER_OPTIONS = List.of(
        new MenuOption(1, "List customers"),
        new MenuOption(2, "Add customer"),
        new MenuOption(3, "Update customer"),
        new MenuOption(4, "Delete customer"),
        new MenuOption(5, "Go back to previous menu")
    );

    private static final List<MenuOption> ORDER_OPTIONS = List.of(
        new MenuOption(1, "List orders"),
        new MenuOption(2, "Add order"),
        new MenuOption(3, "Update order"),
        new MenuOption(4, "Delete order"),
        new MenuOption(5, "Go back to previous menu")
    );

    private static final List<MenuOption> REPORT_OPTIONS = List.of(
        new MenuOption(1, "Daily revenue report"),
        new MenuOption(2, "Go back to previous menu")
    );

    private static final List<MenuOption> SETTINGS = List.of(
        new MenuOption(1, "Configure report delivery"),
        new MenuOption(2, "Go back to previous menu")
    );

    private static final Map<Integer, List<MenuOption>> SUB_MENU_OPTIONS = Map.of(
        1, PRODUCT_OPTIONS,
        2, CUSTOMER_OPTIONS,
        3, ORDER_OPTIONS,
        4, REPORT_OPTIONS,
        5, REPORT_OPTIONS,
        6, SETTINGS
    );

    private static final List<MenuOption> EXPORT_OPTIONS = new ArrayList<>();

    static {
        ExportType[] types = ExportType.values();
        IntStream.range(0, types.length)
            .mapToObj(i -> new MenuOption(i + 1, "Export to " + types[i].name()))
            .forEach(EXPORT_OPTIONS::add);
        EXPORT_OPTIONS.add(new MenuOption(EXPORT_OPTIONS.size() + 1, "Go back to previous menu"));
    }

    private static final List<MenuOption> CHART_OPTIONS = new ArrayList<>();

    static {
        ChartType[] types = ChartType.values();
        IntStream.range(0, types.length)
            .mapToObj(i -> new MenuOption(i + 1, String.format("Create %s plot", types[i].name())))
            .forEach(CHART_OPTIONS::add);
        CHART_OPTIONS.add(new MenuOption(CHART_OPTIONS.size() + 1, "Go back to previous menu"));
    }

    private final List<MenuOption> reportDeliveryOptions = new ArrayList<>();

    private final List<String> args;
    private final DependencyFactory dependencyFactory;
    private final Warehouse warehouse;
    private final List<ReportDelivery> reportDeliveries;

    private ReportDelivery activeReportDelivery;

    public Cli(
        List<String> args,
        DependencyFactory dependencyFactory,
        Warehouse warehouse,
        List<ReportDelivery> reportDeliveries) {
        this.args = args;
        this.dependencyFactory = dependencyFactory;
        this.warehouse = warehouse;
        this.reportDeliveries = reportDeliveries;

        activeReportDelivery = reportDeliveries.get(0);

        createReportDeliveryOptions();
    }

    private void createReportDeliveryOptions() {
        int i;
        for (i = 0; i < reportDeliveries.size(); i++) {
            ReportDelivery reportDelivery = reportDeliveries.get(i);
            reportDeliveryOptions.add(new MenuOption(i + 1, String.format("Switch to '%s'", reportDelivery.getName())));
        }
        reportDeliveryOptions.add(new MenuOption(i + 1, "Go back to previous menu"));
    }

    public void run() {
        while (true) {
            displayMainMenu();
            try {
                int mainMenuChoice = chooseMainMenuOption();
                if (mainMenuChoice == -1) {
                    break;
                }
                while (true) {
                    displaySubMenu(mainMenuChoice);
                    try {
                        int subMenuChoice = chooseSubMenuOption(mainMenuChoice);
                        if (subMenuChoice == -1) {
                            break;
                        }
                        doMenuAction(mainMenuChoice, subMenuChoice);
                    } catch (NumberFormatException ex) {
                        System.err.println("Invalid input. Enter a number.");
                    } catch (WarehouseException | IllegalArgumentException | UnsupportedOperationException ex) {
                        System.err.println(ex.getMessage());
                    }
                }
            } catch (NumberFormatException ex) {
                System.err.println("Invalid input. Enter a number.");
            } catch (IllegalArgumentException | UnsupportedOperationException ex) {
                System.err.println(ex.getMessage());
            }
        }
    }

    private void displayMainMenu() {
        displayMenu(MAIN_MENU_OPTIONS);
    }

    private void displaySubMenu(int mainMenuChoice) {
        displayMenu(SUB_MENU_OPTIONS.get(mainMenuChoice));
    }

    private void displayMenu(List<MenuOption> options) {
        options.forEach(o -> System.out.printf("%s.\t%s%n", o.number, o.label));
    }

    private int chooseMainMenuOption() {
        return chooseMenuOption(MAIN_MENU_OPTIONS);
    }

    private int chooseSubMenuOption(int mainMenuChoice) {
        return chooseMenuOption(SUB_MENU_OPTIONS.get(mainMenuChoice));
    }

    private int chooseMenuOption(List<MenuOption> options) {
        System.out.print("Enter a menu option a press RETURN: ");
        int choice = Integer.valueOf(SCANNER.nextLine());

        MenuOption firstOption = options.get(0);
        MenuOption lastOption = options.get(options.size() - 1);

        if (choice < firstOption.number || choice > lastOption.number) {
            throw new IllegalArgumentException("Invalid menu choice. Available currentOptions are: " +
                firstOption.number + " to " + lastOption.number + ".");
        }

        if (choice == lastOption.number) {
            return -1;
        }
        return choice;
    }

    private void doMenuAction(int mainMenuChoice, int subMenuChoice) throws WarehouseException {
        if (mainMenuChoice == 1) {
            doProductAction(subMenuChoice);
        } else if (mainMenuChoice == 2) {
            doCustomerAction(subMenuChoice);
        } else if (mainMenuChoice == 3) {
            doOrderAction(subMenuChoice);
        } else if (mainMenuChoice == 4) {
            doReportAction(subMenuChoice);
        } else if (mainMenuChoice == 5) {
            doChartAction(subMenuChoice);
        } else if (mainMenuChoice == 6) {
            doSettingsAction(subMenuChoice);
        } else {
            throw new IllegalStateException("There are no such menu option, this cannot happen.");
        }
    }

    private void doSettingsAction(int subMenuChoice) {
        if (subMenuChoice == 1) {
            doConfigureReportDelivery();
        } else {
            throw new IllegalStateException("There are no such menu option, this cannot happen.");
        }
    }

    private void doConfigureReportDelivery() {
        displayMenu(reportDeliveryOptions);
        int reportDeliveryChoice = chooseMenuOption(reportDeliveryOptions);
        if (reportDeliveryChoice == -1) {
            return;
        }
        activeReportDelivery = reportDeliveries.get(reportDeliveryChoice - 1);
        System.out.println(String.format("Selected '%s'.", activeReportDelivery.getName()));
    }

    private void doProductAction(int subMenuChoice) throws WarehouseException {
        if (subMenuChoice == 1) {
            doProductList();
        } else if (subMenuChoice == 2) {
            doAddProduct();
        } else if (subMenuChoice == 3) {
            throw new UnsupportedOperationException("Updating products not yet implemented.");
        } else if (subMenuChoice == 4) {
            throw new UnsupportedOperationException("Deleting products not yet implemented.");
        } else {
            throw new IllegalStateException("There are no such menu options, this cannot happen.");
        }
    }

    private void doCustomerAction(int subMenuChoice) throws WarehouseException {
        if (subMenuChoice == 1) {
            doCustomerList();
        } else if (subMenuChoice == 2) {
            throw new UnsupportedOperationException("Adding customers not yet implemented.");
        } else if (subMenuChoice == 3) {
            throw new UnsupportedOperationException("Updating customers Not yet implemented.");
        } else if (subMenuChoice == 4) {
            throw new UnsupportedOperationException("Deleting customers not yet implemented.");
        } else {
            throw new IllegalStateException("There are no such menu options, this cannot happen.");
        }
    }

    private void doOrderAction(int subMenuChoice) throws WarehouseException {
        if (subMenuChoice == 1) {
            doOrderList();
        } else if (subMenuChoice == 2) {
            doAddOrder();
        } else if (subMenuChoice == 3) {
            throw new UnsupportedOperationException("Updating orders not yet implemented.");
        } else if (subMenuChoice == 4) {
            throw new UnsupportedOperationException("Deleting orders not yet implemented.");
        } else {
            throw new IllegalStateException("There are no such menu option, this cannot happen.");
        }
    }

    private void doReportAction(int subMenuChoice) throws WarehouseException {
        Report.Type reportType;
        if (subMenuChoice == 1) {
            reportType = Report.Type.DAILY_REVENUE;
        } else {
            throw new IllegalStateException("There are no such menu option, this cannot happen.");
        }
        Report report = warehouse.generateReport(reportType);

        ExportType exportType;
        displayMenu(EXPORT_OPTIONS);
        int exportMenuChoice = chooseMenuOption(EXPORT_OPTIONS);
        if (exportMenuChoice == -1) {
            return;
        }
        exportType = ExportType.values()[exportMenuChoice - 1];

        CopyByteArrayOutputStream cos = new CopyByteArrayOutputStream(System.out);
        doReportExport(report, exportType, new PrintStream(cos));

        try {
            activeReportDelivery.deliver(reportType, exportType, cos.toByteArray());
        } catch (ReportDeliveryException ex) {
            System.err.println(ex.getMessage());
        }
    }

    private void doChartAction(int subMenuChoice) throws WarehouseException {
        Report.Type reportType;
        if (subMenuChoice == 1) {
            reportType = Report.Type.DAILY_REVENUE;
        } else {
            throw new IllegalStateException("There are no such menu option, this cannot happen.");
        }
        Report report = warehouse.generateReport(reportType);

        ChartType chartType;
        displayMenu(CHART_OPTIONS);
        int exportMenuChoice = chooseMenuOption(CHART_OPTIONS);
        if (exportMenuChoice == -1) {
            return;
        }
        chartType = ChartType.values()[exportMenuChoice - 1];

        ChartPlotter plotter = dependencyFactory.newPlotter(reportType, chartType);

        try {
            File file = Files.createTempFile(null, ".png").toFile();
            try (OutputStream out = new FileOutputStream(file)) {
                plotter.plot(report, out);
            }
            System.out.printf("Chart created at: %s%n", file.toPath().toUri());
        } catch (IOException ex) {
            throw new WarehouseException("Problem while creating chart.", ex);
        }
    }

    private void doReportExport(Report report, ExportType type, PrintStream out) {
        Exporter exporter = dependencyFactory.newExporter(report, type, out);
        exporter.export();
    }

    private void doProductList() throws WarehouseException {
        Collection<Product> croducts = warehouse.getProducts();
        int maxIdWidth = 0;
        int maxNameWidth = 0;
        int maxPriceWidth = 0;
        for (Product croduct : croducts) {
            int idWidth = String.valueOf(croduct.getId()).length();
            if (idWidth > maxIdWidth) {
                maxIdWidth = idWidth;
            }
            int nameWidth = croduct.getName().length();
            if (nameWidth > maxNameWidth) {
                maxNameWidth = nameWidth;
            }
            int priceWidth = String.valueOf(croduct.getPrice()).length();
            if (priceWidth > maxPriceWidth) {
                maxPriceWidth = priceWidth;
            }
        }
        String fmt = String.format("\t%%%ss\t\t%%%ss\t\t%%%ss%%n", maxIdWidth, maxNameWidth, maxPriceWidth);
        croducts.forEach(p -> System.out.printf(fmt, p.getId(), p.getName(), p.getPrice()));
    }

    private void doAddProduct() throws WarehouseException {
        System.out.print("Enter the product's name and press RETURN: ");
        String name = SCANNER.nextLine();
        int price;
        try {
            System.out.print("Enter the product's price and press RETURN: ");
            price = Integer.valueOf(SCANNER.nextLine());
        } catch (InputMismatchException ex) {
            throw new IllegalArgumentException("The product's price must be an integer.", ex);
        }
        warehouse.addProduct(name, price);
    }

    private void doCustomerList() throws WarehouseException {
        Collection<Customer> customers = warehouse.getCustomers();
        int maxIdWidth = 0;
        int maxNameWidth = 0;
        for (Customer customer : customers) {
            int idWidth = String.valueOf(customer.getId()).length();
            if (idWidth > maxIdWidth) {
                maxIdWidth = idWidth;
            }
            int nameWidth = customer.getName().length();
            if (nameWidth > maxNameWidth) {
                maxNameWidth = nameWidth;
            }
        }
        String fmt = String.format("\t%%%ss\t\t%%%ss%%n", maxIdWidth, maxNameWidth);
        customers.forEach(c -> System.out.printf(fmt, c.getId(), c.getName()));
    }

    private void doOrderList() throws WarehouseException {
        Collection<Order> orders = warehouse.getOrders();
        int maxIdWidth = 0;
        int maxCustomerNameWidth = 0;
        int maxCustomerIdWidth = 0;
        int maxTotalPriceWidth = 0;
        for (Order order : orders) {
            int idWidth = String.valueOf(order.getId()).length();
            if (idWidth > maxIdWidth) {
                maxIdWidth = idWidth;
            }
            int customerNameWidth = order.getCustomer().getName().length();
            if (customerNameWidth > maxCustomerNameWidth) {
                maxCustomerNameWidth = customerNameWidth;
            }
            int customerIdWidth = String.valueOf(order.getCustomer().getId()).length();
            if (customerIdWidth > maxCustomerIdWidth) {
                maxCustomerIdWidth = customerIdWidth;
            }
            int totalPriceIdWidth = String.valueOf(order.getTotalPrice()).length();
            if (totalPriceIdWidth > maxTotalPriceWidth) {
                maxTotalPriceWidth = totalPriceIdWidth;
            }
        }
        String fmt = String.format("\t%%%ss %%s\t\t%%%ss (%%%ss)\t\t%%%ss [%%s]%%n",
            maxIdWidth, maxCustomerNameWidth, maxCustomerIdWidth, maxTotalPriceWidth);
        orders.forEach(o -> System.out.printf(fmt, o.getId(), o.getDate(), o.getCustomer().getName(),
            o.getCustomer().getId(), o.getTotalPrice(), o.isPending() ? "pending" : "fulfilled"));
    }

    private void doAddOrder() throws WarehouseException {
        int customerId;
        try {
            System.out.print("Enter the customer's ID and press RETURN: ");
            customerId = Integer.valueOf(SCANNER.nextLine());
        } catch (InputMismatchException ex) {
            throw new IllegalArgumentException("The product's customerId must be an integer.", ex);
        }
        Map<Integer, Integer> quantities = new HashMap<>();
        while (true) {
            System.out.print("Enter the product's ID (or nothing to stop) and press RETURN: ");
            String productIdLine = SCANNER.nextLine();
            if (productIdLine.isBlank()) {
                break;
            }
            System.out.print("Enter the desired quantity (or nothing to stop) and press RETURN: ");
            String quantityLine = SCANNER.nextLine();
            if (quantityLine.isBlank()) {
                break;
            }
            int productId;
            try {
                productId = Integer.valueOf(productIdLine);
            } catch (InputMismatchException ex) {
                throw new IllegalArgumentException("The product's ID must be an integer.", ex);
            }
            int quantity;
            try {
                quantity = Integer.valueOf(quantityLine);
            } catch (InputMismatchException ex) {
                throw new IllegalArgumentException("The quantity must be an integer.", ex);
            }
            quantities.put(productId, quantity);
        }
        warehouse.addOrder(customerId, quantities);
    }
}
