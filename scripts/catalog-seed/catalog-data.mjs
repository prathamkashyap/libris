export const SAMPLE_CATALOG_VERSION = "libris-starter-catalog-v1";

export const CATALOG_COLLECTIONS = Object.freeze(["books", "magazines", "newspapers"]);

export const CATALOG_DATA = Object.freeze([
  ...[
    ["Clean Code", "Robert C. Martin", "Computing"],
    ["The Pragmatic Programmer", "Andrew Hunt and David Thomas", "Computing"],
    ["Code: The Hidden Language of Computer Hardware and Software", "Charles Petzold", "Computing"],
    ["The Clean Coder", "Robert C. Martin", "Computing"],
    ["A Brief History of Time", "Stephen Hawking", "Science"],
    ["Cosmos", "Carl Sagan", "Science"],
    ["The Selfish Gene", "Richard Dawkins", "Science"],
    ["1984", "George Orwell", "Fiction"],
    ["The Hobbit", "J. R. R. Tolkien", "Fiction"],
    ["Pride and Prejudice", "Jane Austen", "Fiction"],
    ["To Kill a Mockingbird", "Harper Lee", "Fiction"],
    ["Sapiens", "Yuval Noah Harari", "General knowledge"],
    ["A Short History of Nearly Everything", "Bill Bryson", "General knowledge"],
    ["Factfulness", "Hans Rosling, Ola Rosling, and Anna Rosling Rönnlund", "General knowledge"],
  ].map(([title, author, category]) => ({
    collection: "books",
    title,
    author,
    category: `Sample — ${category}`,
    isbn: null,
    publishedDate: null,
    available: true,
  })),
  ...[
    ["National Geographic", "Science"],
    ["Scientific American", "Science"],
    ["Wired", "Computing"],
  ].map(([title, category]) => ({
    collection: "magazines",
    title,
    publisher: null,
    issueDate: null,
    category: `Sample — ${category}`,
    featuredArticle: null,
    available: true,
  })),
  ...["The Hindu", "The Indian Express", "The Times of India"].map(title => ({
    collection: "newspapers",
    title: `${title} — Sample edition`,
    publisher: null,
    publicationDate: null,
    topHeadlines: "Illustrative sample entry — not current news",
    available: true,
  })),
]);

export function naturalKey(record) {
  const title = normalizeTitle(record.title);
  if (record.collection === "newspapers") {
    return `${record.collection}:${normalizeTitle(stripSampleEdition(record.title))}`;
  }
  return `${record.collection}:${title}`;
}

export function normalizeTitle(value) {
  return String(value ?? "")
    .normalize("NFKC")
    .trim()
    .replace(/\s+/g, " ")
    .toLocaleLowerCase("en");
}

function stripSampleEdition(title) {
  return title.replace(/\s+—\s+Sample edition$/i, "");
}
