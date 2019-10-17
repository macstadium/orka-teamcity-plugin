ko.bindingHandlers.initValue = {
    init: function (element, valueAccessor) {
        var value = valueAccessor();
        if (!ko.isWriteableObservable(value)) {
            throw new Error('Knockout "initValue" binding expects an observable.');
        }
        value(element.value);
    }
};