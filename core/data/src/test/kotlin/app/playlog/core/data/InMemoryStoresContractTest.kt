package app.playlog.core.data

import app.playlog.core.testing.AccountStoreContract
import app.playlog.core.testing.LibraryStoreContract

class InMemoryLibraryStoreContractTest : LibraryStoreContract() {
    override fun createStore(): LibraryStore = InMemoryLibraryStore()
}

class InMemoryAccountStoreContractTest : AccountStoreContract() {
    override fun createStore(): AccountStore = InMemoryAccountStore()
}
