export interface Guide {
    title: string;
    type: string;
    url: string;
    summary: string;
    keywords: string;
    content?: [string] | string;
    categories?: string;
    origin?: string;
}