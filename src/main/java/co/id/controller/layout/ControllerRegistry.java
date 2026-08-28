package co.id.controller.layout;

public class ControllerRegistry {
    private static MainLayoutController mainLayoutController;
    private static SidebarController sidebarController;
    private static NavbarController navbarController;
    
    public static MainLayoutController getMainLayoutController() {
        return mainLayoutController;
    }

    public static void setMainLayoutController(MainLayoutController mainLayoutController) {
        ControllerRegistry.mainLayoutController = mainLayoutController;
    }

    public static SidebarController getSidebarController() {
        return sidebarController;
    }

    public static void setSidebarController(SidebarController sidebarController) {
        ControllerRegistry.sidebarController = sidebarController;
    }
    
    public static NavbarController getNavbarController() {
        return navbarController;
    }
    public static void setNavbarController(NavbarController navbarController) {
        ControllerRegistry.navbarController = navbarController;
    }
}
