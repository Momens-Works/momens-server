package works.momens.server.minsu.llm.google;

import works.momens.server.minsu.llm.ModelSelection;

interface GoogleClientFactory {

  GoogleSdkClient create(ModelSelection selection);
}
