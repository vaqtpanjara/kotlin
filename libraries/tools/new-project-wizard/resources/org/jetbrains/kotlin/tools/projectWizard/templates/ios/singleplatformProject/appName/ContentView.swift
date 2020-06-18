import SwiftUI
import $sharedModuleName

struct ContentView: View {
    var body: some View {
        Text(Greeting().greeting())
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
