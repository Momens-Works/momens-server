package works.momens.server.minsu.internal.google;

import works.momens.server.minsu.internal.llm.ModelSelection;

interface GoogleClientFactory {

  GoogleSdkClient create(ModelSelection selection);
}
