import {Guide} from "./types";

export class LocalSearch {

    static guides: Guide[] = null;

    static queryDocumentGuides(selector = "qs-target qs-guide"): Guide[] {
        const elements = document.querySelectorAll(selector);
        const guides: Guide[] | undefined = elements ? [] : null;

        for (let i = 0; i < elements.length; i++) {
            const element: Element = elements[i];
            guides.push({
                title: element.getAttribute("title"),
                categories: element.getAttribute("categories"),
                type: element.getAttribute("type"),
                url: element.getAttribute("url"),
                summary: element.getAttribute("summary"),
                keywords: element.getAttribute("keywords"),
                content: element.getAttribute("content"),
                origin: element.getAttribute("origin")
            });
        }
        return guides;
    }

    static enableLocalSearch(selector?: string) {
        LocalSearch.guides = LocalSearch.queryDocumentGuides(selector);
        if (LocalSearch.guides != null) {
            console.log("LocalSearch is ready with " + LocalSearch.guides.length + " guides found.");
        }
    }

    static search(params: any) {
        const guides = LocalSearch.guides;
        if (guides == null) {
            return null;
        }
        const terms: string[] = [];
        if (params["q"]) {
            terms.push(...params["q"].split(' ').map((token: string) => token.trim()));
        }
        const categories = [];
        if (params["categories"]) {
            if (Array.isArray(params["categories"])) {
                categories.push(...params["categories"]);
            } else {
                categories.push(params["categories"]);
            }
        }

        return guides
            .filter(g => {
                let match = true
                if (match && categories.length > 0) {
                    match = LocalSearch.containsAllCaseInsensitive(g.categories, categories)
                }
                if (match && terms.length > 0) {
                    match = LocalSearch.containsAllCaseInsensitive(`${g.keywords}${g.summary}${g.title}${g.categories}`, terms)
                }
                return match
            })
    }

    private static containsAllCaseInsensitive(elem: string, terms: string[]) {
        const text = (elem ? elem : '').toLowerCase();
        for (let i in terms) {
            if (text.indexOf(terms[i].toLowerCase()) < 0) {
                return false
            }
        }
        return true
    }
}