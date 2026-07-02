import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        // BGTaskScheduler launch handlers must be registered before the app
        // finishes launching (panel-alerts background refresh).
        MainViewControllerKt.registerBgTasks()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
